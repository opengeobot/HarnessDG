package com.modelhub.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.modelhub.catalog.domain.JobEntity;
import com.modelhub.catalog.domain.JobEventEntity;
import com.modelhub.catalog.repo.JobEventRepository;
import com.modelhub.catalog.repo.JobRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Jobs 查询面集成测试（05 §1）：状态查询授权 / 取消幂等 / SSE 事件流回放与 Last-Event-ID 续传。
 * Job 通过仓库 DELETE 受理产生（202 JobEnvelope），随后由 Outbox 异步推进到终态。
 */
class JobsEventsTest extends CatalogTestSupport {

    @LocalServerPort
    int port;

    @Autowired
    JobRepository jobRepo;

    @Autowired
    JobEventRepository jobEventRepo;

    private static final HttpClient HTTP =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Test
    void jobQueryAuthorizationAndLifecycle() throws Exception {
        Session owner = newUser("jobsq");
        Session stranger = newUser("jobsq2");
        String jobId = deleteActiveRepo(owner);

        // 创建者可见：契约必填字段 + 可选字段全部 null-safe 输出
        ResponseEntity<String> resp = getJob(owner.accessToken(), jobId);
        assertEquals(200, resp.getStatusCode().value());
        JsonNode job = dataNode(resp);
        assertEquals(jobId, job.path("id").asText());
        assertEquals("repository.deletion", job.path("type").asText());
        assertFalse(job.path("status").asText().isBlank());
        assertFalse(job.path("createdAt").asText().isBlank());
        for (String optional : List.of("progressCurrent", "progressTotal", "progressMessage",
                "resultSummary", "errorCode", "startedAt", "finishedAt")) {
            assertTrue(job.has(optional), "缺少可选字段 " + optional);
        }

        // 非创建者 404（不泄漏存在性）；匿名 401；platform_admin 可见
        assertEquals(404, getJob(stranger.accessToken(), jobId).getStatusCode().value());
        assertEquals(401, getJob(null, jobId).getStatusCode().value());
        Session root = login("platform-root", "Boot-Strap-1x");
        assertEquals(200, getJob(root.accessToken(), jobId).getStatusCode().value());

        // 异步推进到终态后 startedAt/finishedAt 由 worker 维护
        JsonNode done = awaitJobStatus(owner.accessToken(), jobId, "succeeded");
        assertTrue(done.path("startedAt").isTextual(), "startedAt 应被写入: " + done);
        assertTrue(done.path("finishedAt").isTextual(), "finishedAt 应被写入: " + done);

        // job_events 真相源：单调递增且包含 created/status_changed
        JobEntity entity = jobRepo.findByPublicId(UUID.fromString(jobId)).orElseThrow();
        List<JobEventEntity> events = jobEventRepo.findByJobIdOrderBySequenceAsc(entity.getId());
        assertTrue(events.size() >= 3, "应有 created/running/succeeded 至少 3 条事件，实际=" + events.size());
        for (int i = 1; i < events.size(); i++) {
            assertTrue(events.get(i).getSequence() > events.get(i - 1).getSequence(), "sequence 必须单调递增");
        }
        assertEquals("created", events.get(0).getEventType());
        assertTrue(events.stream().anyMatch(e -> "status_changed".equals(e.getEventType())));
        assertTrue(events.stream().anyMatch(e -> e.getData() != null && e.getData().contains("succeeded")));
    }

    @Test
    void cancelSemantics() throws Exception {
        Session owner = newUser("jobcx");
        Session stranger = newUser("jobcx2");

        // queued/running 阶段取消 → 202；与 Outbox 轮询存在竞态，用新 Job 重试保证稳定
        String cancelRequestedJob = null;
        for (int attempt = 0; attempt < 3 && cancelRequestedJob == null; attempt++) {
            String candidate = deleteActiveRepo(owner);
            ResponseEntity<String> cancel = cancelJob(owner.accessToken(), candidate);
            if (cancel.getStatusCode().value() == 202) {
                cancelRequestedJob = candidate;
                assertEquals("cancel_requested", dataNode(cancel).path("status").asText());
                assertEquals("repository.deletion", dataNode(cancel).path("type").asText());
            }
        }
        assertNotNull(cancelRequestedJob, "3 次内应能在非终态窗口完成取消");

        // 重复取消幂等：仍 202 且状态保持 cancel_requested（该状态无 worker 推进，稳定）
        ResponseEntity<String> again = cancelJob(owner.accessToken(), cancelRequestedJob);
        assertEquals(202, again.getStatusCode().value());
        assertEquals("cancel_requested", dataNode(again).path("status").asText());

        // 非创建者不可取消 → 404
        assertEquals(404, cancelJob(stranger.accessToken(), cancelRequestedJob).getStatusCode().value());

        // 终态后取消 → 409 CONFLICT
        String terminalJob = deleteActiveRepo(owner);
        awaitJobStatus(owner.accessToken(), terminalJob, "succeeded");
        ResponseEntity<String> terminalCancel = cancelJob(owner.accessToken(), terminalJob);
        assertEquals(409, terminalCancel.getStatusCode().value());
    }

    @Test
    void sseReplayResumeAndAuthorization() throws Exception {
        Session owner = newUser("jobsse");
        Session stranger = newUser("jobsse2");
        String jobId = deleteActiveRepo(owner);
        awaitJobStatus(owner.accessToken(), jobId, "succeeded");

        // 全量回放：200 text/event-stream，事件按序送达，终态后流收口（EOF）
        SseResult full = sse(owner.accessToken(), jobId, null);
        assertEquals(200, full.status());
        assertTrue(full.contentType().startsWith("text/event-stream"),
                "content-type 应为 text/event-stream，实际=" + full.contentType());
        assertTrue(full.eof(), "终态 Job 的事件流应正常收口");
        List<Long> ids = seqIds(full.lines());
        assertEquals(List.of(1L, 2L, 3L), ids, "应为 created/running/succeeded 三条: " + full.lines());
        assertTrue(full.lines().stream().anyMatch(l -> l.startsWith("event:created")));
        assertTrue(full.lines().stream().anyMatch(l -> l.startsWith("event:status_changed")));
        assertTrue(full.lines().stream().anyMatch(l -> l.startsWith("data:")
                        && l.contains("\"status\":\"succeeded\"")),
                "末条事件 data 应携带事件时刻 status=succeeded");

        // Last-Event-ID=1 → 仅回放后续事件（> 1）
        SseResult resumed = sse(owner.accessToken(), jobId, "1");
        assertEquals(200, resumed.status());
        assertEquals(List.of(2L, 3L), seqIds(resumed.lines()));

        // Last-Event-ID=0 → 从头全量回放（首条 sequence=1，0 = 1-1 属于可续传范围）
        SseResult fromZero = sse(owner.accessToken(), jobId, "0");
        assertEquals(200, fromZero.status());
        assertEquals(List.of(1L, 2L, 3L), seqIds(fromZero.lines()));

        // Last-Event-ID 早于保留历史 → 410 EVENT_HISTORY_EXPIRED
        SseResult expired = sse(owner.accessToken(), jobId, "-1");
        assertEquals(410, expired.status());
        assertEquals("EVENT_HISTORY_EXPIRED", errorCodeOf(expired.lines()));

        // 非整数 → 400
        assertEquals(400, sse(owner.accessToken(), jobId, "abc").status());

        // 授权：非创建者 404；匿名 401
        assertEquals(404, sse(stranger.accessToken(), jobId, null).status());
        assertEquals(401, sse(null, jobId, null).status());
    }

    // ---------- 助手 ----------

    /** 建仓库 → active → DELETE 受理，返回 202 JobEnvelope 的 job id。 */
    private String deleteActiveRepo(Session owner) throws Exception {
        String ns = userNamespaceId(owner);
        String repoId = dataNode(createRepo(owner, ns, unique("Jobs"), "public", null, null))
                .path("id").asText();
        JsonNode active = awaitLifecycleHttp(owner.accessToken(), repoId, "active");
        ResponseEntity<String> deleted = deleteRepo(owner.accessToken(), repoId,
                UUID.randomUUID().toString(), etagOfVersion(active.path("version").asLong()));
        assertEquals(202, deleted.getStatusCode().value());
        return dataNode(deleted).path("id").asText();
    }

    private ResponseEntity<String> getJob(String token, String jobId) {
        HttpHeaders h = token == null ? ipHeaders() : bearer(token);
        return rest.exchange("/api/v1/jobs/" + jobId, HttpMethod.GET, new HttpEntity<>(h), String.class);
    }

    private ResponseEntity<String> cancelJob(String token, String jobId) {
        return rest.exchange("/api/v1/jobs/" + jobId + ":cancel", HttpMethod.POST,
                new HttpEntity<>(bearer(token)), String.class);
    }

    private JsonNode awaitJobStatus(String token, String jobId, String expected) throws Exception {
        long deadline = System.currentTimeMillis() + 60_000;
        JsonNode last = null;
        while (System.currentTimeMillis() < deadline) {
            ResponseEntity<String> resp = getJob(token, jobId);
            if (resp.getStatusCode().is2xxSuccessful()) {
                last = dataNode(resp);
                if (expected.equals(last.path("status").asText())) {
                    return last;
                }
            }
            Thread.sleep(200);
        }
        throw new AssertionError("等待 job status=" + expected + " 超时，最后="
                + (last == null ? "?" : last.path("status").asText()));
    }

    private record SseResult(int status, String contentType, List<String> lines, boolean eof) {}

    /** 原生 HttpClient 拉取 SSE：读到 EOF（流收口）或截止时间。 */
    private SseResult sse(String token, String jobId, String lastEventId) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/jobs/" + jobId + "/events"))
                .timeout(Duration.ofSeconds(30))
                .GET();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (lastEventId != null) {
            builder.header("Last-Event-ID", lastEventId);
        }
        HttpResponse<InputStream> resp = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
        List<String> lines = new ArrayList<>();
        boolean eof = false;
        long deadline = System.currentTimeMillis() + 20_000;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resp.body(), StandardCharsets.UTF_8))) {
            while (System.currentTimeMillis() < deadline) {
                String line = reader.readLine();
                if (line == null) {
                    eof = true;
                    break;
                }
                lines.add(line);
            }
        }
        return new SseResult(resp.statusCode(),
                resp.headers().firstValue("Content-Type").orElse(""), lines, eof);
    }

    private static List<Long> seqIds(List<String> lines) {
        return lines.stream()
                .filter(l -> l.startsWith("id:"))
                .map(l -> Long.parseLong(l.substring(3).trim()))
                .toList();
    }

    /** 错误响应（非 SSE）为单段 JSON body，拼接后取 code。 */
    private static String errorCodeOf(List<String> lines) {
        String body = String.join("", lines);
        try {
            return JSON.readTree(body).path("code").asText();
        } catch (Exception e) {
            throw new IllegalStateException("解析错误响应失败: " + body, e);
        }
    }
}
