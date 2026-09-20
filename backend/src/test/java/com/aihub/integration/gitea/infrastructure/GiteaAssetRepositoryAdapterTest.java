/*
 * 功能: Gitea 仓库开通/投影适配器的请求路径、幂等与写入动词回归测试。
 *       以 JDK 内置 HttpServer 作为真实 HTTP 端点，捕获 RestClient 实际发出的请求行，
 *       从而能判别「owner/repo 的斜杠被百分号编码为 %2F 导致 404」这一缺陷。
 * 时间: 2026-09-20
 * 作者: AxeXie
 */
package com.aihub.integration.gitea.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aihub.asset.domain.AssetRepositoryProvisioner.ProvisionRequest;
import com.aihub.asset.domain.AssetRepositoryRef;
import com.aihub.asset.domain.AssetType;
import com.aihub.asset.domain.Visibility;
import com.aihub.shared.error.DependencyException;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.error.ValidationException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * 覆盖 TASK-P0BR-062 的三条核心不变量：
 *
 * <ol>
 *   <li><b>路径拼接</b>：{@code owner/repo} 必须落在两个独立路径段，请求里不得出现 {@code %2F}；
 *       这是原缺陷的直接判别式——旧实现整体作为一个模板变量，斜杠被编码后必然 404。</li>
 *   <li><b>建仓幂等</b>：仓库已存在（409）时复用既有仓库，使部分成功后的重试可收敛；
 *       但真实依赖故障仍必须上抛可重试错误，不得被幂等分支吞掉。</li>
 *   <li><b>写入动词</b>：Gitea 的 contents 接口对新建与更新要求不同动词——新建走 {@code POST}
 *       且不得带 {@code sha}，更新走 {@code PUT} 且 {@code sha} 必填（缺失返回 422
 *       {@code [SHA]: Required}）。因此「文件已存在」的重试路径必须自动落到 PUT。</li>
 * </ol>
 *
 * <p>本测试使用真实 HTTP 连接（JDK {@link HttpServer}），因此断言的是 RestClient
 * 真正构造出的请求行，而不是 mock 的交互形状；证据等级为 E2（无真实 Gitea，
 * 端到端结论以 E4 为准）。
 */
class GiteaAssetRepositoryAdapterTest {

    private HttpServer server;
    private String baseUrl;
    private final List<String> requestLines = new CopyOnWriteArrayList<>();
    private final List<String> requestBodies = new CopyOnWriteArrayList<>();
    /** 「METHOD 原始路径」-> 状态码；未声明的端点一律 404。 */
    private final Map<String, Integer> statusByRequest = new ConcurrentHashMap<>();
    /** 「METHOD 原始路径」-> 响应体 JSON。 */
    private final Map<String, String> bodyByRequest = new ConcurrentHashMap<>();
    /** 一次性响应：优先于静态桩，用于模拟「读后写」竞态等时序场景。 */
    private final Map<String, Deque<Stub>> oneShotResponses = new ConcurrentHashMap<>();

    private record Stub(int status, String body) {
    }

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        // 记录原始请求行（含未解码的路径），这是断言 %2F 的关键。
        String rawPath = exchange.getRequestURI().getRawPath();
        String query = exchange.getRequestURI().getRawQuery();
        String key = exchange.getRequestMethod() + " " + rawPath;
        requestLines.add(key + (query == null ? "" : "?" + query));
        try (InputStream in = exchange.getRequestBody()) {
            requestBodies.add(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        Stub oneShot = pollOneShot(key);
        int status = oneShot != null ? oneShot.status()
                : statusByRequest.getOrDefault(key, 404);
        String body = oneShot != null ? oneShot.body()
                : bodyByRequest.getOrDefault(key, "{}");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private Stub pollOneShot(String key) {
        Deque<Stub> queue = oneShotResponses.get(key);
        return queue == null ? null : queue.poll();
    }

    /** 声明端点响应；同一路径的不同动词必须分别声明。 */
    private void stub(String method, String path, int status) {
        stub(method, path, status, "{}");
    }

    private void stub(String method, String path, int status, String body) {
        statusByRequest.put(method + " " + path, status);
        bodyByRequest.put(method + " " + path, body);
    }

    /** 声明只生效一次的响应，之后回落到静态桩。 */
    private void stubOnce(String method, String path, int status, String body) {
        oneShotResponses.computeIfAbsent(method + " " + path, ignored -> new ArrayDeque<>())
                .add(new Stub(status, body));
    }

    /** 期望的请求行；用于断言具体端点。 */
    private List<String> linesFor(String methodAndPathPrefix) {
        List<String> matched = new ArrayList<>();
        for (String line : requestLines) {
            if (line.startsWith(methodAndPathPrefix)) {
                matched.add(line);
            }
        }
        return matched;
    }

    /**
     * 精确匹配「方法 + 原始路径 + 查询串」，避免前缀匹配把
     * {@code .../contents/README.md} 也算成仓库查询。
     */
    private List<String> exactLinesFor(String method, String rawPath, String query) {
        String expected = method + " " + rawPath + (query == null ? "" : "?" + query);
        List<String> matched = new ArrayList<>();
        for (String line : requestLines) {
            if (line.equals(expected)) {
                matched.add(line);
            }
        }
        return matched;
    }

    /** 取匹配请求行对应位置的请求体；未命中返回 null。 */
    private String bodyOf(String methodAndPathPrefix) {
        for (int index = 0; index < requestLines.size(); index++) {
            if (requestLines.get(index).startsWith(methodAndPathPrefix)) {
                return requestBodies.get(index);
            }
        }
        return null;
    }

    private GiteaAssetRepositoryProvisioner provisioner() {
        return new GiteaAssetRepositoryProvisioner(RestClient.builder(), properties());
    }

    /** 默认超时按属性默认值（连接 5s / 读取 30s），测试中按需覆盖读取超时。 */
    private GiteaProperties properties() {
        return new GiteaProperties(true, baseUrl, "test-token", "main", 0, 0);
    }

    private static ProvisionRequest request() {
        return new ProvisionRequest("e4owner", "demo-model", AssetType.MODEL, "desc",
                Visibility.PUBLIC, "# Demo\n", "schemaVersion: 1\n");
    }

    /**
     * 全新仓库的建仓链路：组织建仓 201，{@code asset.yaml} 尚不存在（走新建），
     * {@code README.md} 由 {@code auto_init} 生成（走更新）。
     */
    private void stubHappyPath() {
        stub("POST", "/api/v1/orgs/e4owner/repos", 201,
                "{\"full_name\":\"e4owner/demo-model\",\"html_url\":\"h\",\"clone_url\":\"c\"}");
        stub("GET", "/api/v1/repos/e4owner/demo-model/contents/asset.yaml", 404);
        stub("POST", "/api/v1/repos/e4owner/demo-model/contents/asset.yaml", 201);
        stub("GET", "/api/v1/repos/e4owner/demo-model/contents/README.md", 200, "{\"sha\":\"abc123\"}");
        stub("PUT", "/api/v1/repos/e4owner/demo-model/contents/README.md", 200);
    }

    @Nested
    @DisplayName("路径拼接：owner/repo 必须落在两个独立路径段")
    class PathComposition {

        @Test
        @DisplayName("写 asset.yaml 与覆盖 README 的请求路径不含 %2F")
        void provisionSendsUnencodedOwnerAndRepoPathSegments() {
            stubHappyPath();

            AssetRepositoryRef ref = provisioner().provision(request());

            assertThat(ref.fullName()).isEqualTo("e4owner/demo-model");

            List<String> fileRequests = new ArrayList<>();
            fileRequests.addAll(linesFor("GET /api/v1/repos/e4owner/demo-model/contents/"));
            fileRequests.addAll(linesFor("POST /api/v1/repos/e4owner/demo-model/contents/"));
            fileRequests.addAll(linesFor("PUT /api/v1/repos/e4owner/demo-model/contents/"));
            assertThat(fileRequests)
                    .as("新建 asset.yaml、覆盖 README 共 4 次文件交互，全部命中未编码的 owner/repo 路径")
                    .hasSize(4);

            assertThat(requestLines)
                    .as("原缺陷会把 owner/repo 编码成 owner%%2Frepo，请求落到不存在的路径并 404")
                    .noneMatch(line -> line.contains("%2F") || line.contains("%2f"));

            // 旧实现的失败形态：被整体编码后的路径不存在，服务端只会返回 404。
            assertThat(linesFor("PUT /api/v1/repos/e4owner%2Fdemo-model/contents/")).isEmpty();
            assertThat(linesFor("POST /api/v1/repos/e4owner%2Fdemo-model/contents/")).isEmpty();
        }

        @Test
        @DisplayName("新建文件走 POST 且位于正确路径，内容为 base64 编码")
        void newFileIsCreatedByPostWithEncodedContent() {
            stubHappyPath();

            provisioner().provision(request());

            List<String> postLines = linesFor("POST /api/v1/repos/e4owner/demo-model/contents/asset.yaml");
            assertThat(postLines)
                    .as("文件不存在时必须走 POST 新建：PUT 缺少 sha 会被 Gitea 以 422 拒绝")
                    .hasSize(1);
            // asset.yaml 的写入明确带 branch，且内容为 base64（不含明文换行）。
            assertThat(postLines.get(0)).doesNotContain("schemaVersion");
            String postBody = bodyOf("POST /api/v1/repos/e4owner/demo-model/contents/asset.yaml");
            assertThat(postBody).isNotNull();
            String expectedBase64 = Base64.getEncoder()
                    .encodeToString("schemaVersion: 1\n".getBytes(StandardCharsets.UTF_8));
            assertThat(postBody)
                    .as("asset.yaml 应以 base64 提交，保持 UTF-8 原样")
                    .contains(expectedBase64);
        }

        @Test
        @DisplayName("Card 投影读取同样使用未编码路径")
        void cardProjectionReadsUnencodedPath() {
            String content = Base64.getEncoder()
                    .encodeToString("# Card\n".getBytes(StandardCharsets.UTF_8));
            stub("GET", "/api/v1/repos/e4owner/demo-model/contents/README.md", 200,
                    "{\"content\":\"" + content + "\"}");
            stub("GET", "/api/v1/repos/e4owner/demo-model/contents/asset.yaml", 200,
                    "{\"content\":\"" + content + "\"}");
            stub("GET", "/api/v1/repos/e4owner/demo-model/branches/main", 200,
                    "{\"commit\":{\"id\":\"deadbeef\"}}");

            GiteaProperties properties = properties();
            var projection = new GiteaCardProjection(RestClient.builder(), properties)
                    .fetchCard("e4owner/demo-model", null);

            assertThat(projection.readme()).isEqualTo("# Card\n");
            assertThat(projection.assetYaml()).isEqualTo("# Card\n");
            assertThat(projection.sourceCommit()).isEqualTo("deadbeef");
            assertThat(requestLines).noneMatch(line -> line.contains("%2F") || line.contains("%2f"));
        }

        @Test
        @DisplayName("非法 full_name 明确拒绝，不发出注定 404 的请求")
        void malformedFullNameIsRejectedWithoutRequest() {
            assertThatThrownBy(() -> GiteaRepoPath.parse(null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("missing");
            assertThatThrownBy(() -> GiteaRepoPath.parse("no-slash"))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("owner/repo");
            assertThatThrownBy(() -> GiteaRepoPath.parse("a/b/c"))
                    .isInstanceOf(ValidationException.class);
            assertThatThrownBy(() -> GiteaRepoPath.parse("owner/"))
                    .isInstanceOf(ValidationException.class);
            assertThatThrownBy(() -> GiteaRepoPath.parse("/repo"))
                    .isInstanceOf(ValidationException.class);

            assertThatThrownBy(() -> {
                // 建仓返回一个非法的 full_name，应被明确拒绝而不是拼出错误 URL。
                stub("POST", "/api/v1/orgs/e4owner/repos", 201, "{\"full_name\":\"broken\"}");
                provisioner().provision(request());
            }).isInstanceOf(ValidationException.class);

            assertThat(linesFor("POST /api/v1/repos/")).as("非法坐标不得触发写文件请求").isEmpty();
            assertThat(linesFor("PUT ")).isEmpty();
        }

        @Test
        @DisplayName("round-trip：parse 后 fullName() 保持原值")
        void parseRoundTripKeepsFullName() {
            GiteaRepoPath path = GiteaRepoPath.parse("owner/repo");
            assertThat(path.owner()).isEqualTo("owner");
            assertThat(path.repo()).isEqualTo("repo");
            assertThat(path.fullName()).isEqualTo("owner/repo");
        }
    }

    @Nested
    @DisplayName("建仓幂等：已存在则复用，真实故障仍可重试")
    class CreateIdempotency {

        @Test
        @DisplayName("组织下已存在同名仓库（409）时复用并继续写入，不再当作失败")
        void existingRepoUnderOrgIsReusedInsteadOfFailing() {
            // 组织建仓 409；回查返回既有仓库；随后写文件成功。
            stub("POST", "/api/v1/orgs/e4owner/repos", 409, "{\"message\":\"repository already exists\"}");
            stub("GET", "/api/v1/repos/e4owner/demo-model", 200,
                    "{\"full_name\":\"e4owner/demo-model\",\"html_url\":\"h\",\"clone_url\":\"c\"}");
            stub("GET", "/api/v1/repos/e4owner/demo-model/contents/asset.yaml", 404);
            stub("POST", "/api/v1/repos/e4owner/demo-model/contents/asset.yaml", 201);
            stub("GET", "/api/v1/repos/e4owner/demo-model/contents/README.md", 200, "{\"sha\":\"abc123\"}");
            stub("PUT", "/api/v1/repos/e4owner/demo-model/contents/README.md", 200);

            AssetRepositoryRef ref = provisioner().provision(request());

            assertThat(ref.fullName()).isEqualTo("e4owner/demo-model");
            assertThat(exactLinesFor("GET", "/api/v1/repos/e4owner/demo-model", null))
                    .as("409 后应回查既有仓库以复用，而不是直接失败")
                    .hasSize(1);
            assertThat(linesFor("POST /api/v1/repos/e4owner/demo-model/contents/asset.yaml"))
                    .as("复用既有仓库后仍需补齐 asset.yaml")
                    .hasSize(1);
        }

        @Test
        @DisplayName("回退到用户名下建仓时同样复用已存在仓库")
        void existingRepoUnderUserIsReusedAfterFallback() {
            // 组织路径 404（命名空间不是组织）→ 回退；用户名下 409 → 回查当前用户 → 复用。
            stub("POST", "/api/v1/orgs/e4owner/repos", 404);
            stub("POST", "/api/v1/user/repos", 409, "{\"message\":\"repository already exists\"}");
            stub("GET", "/api/v1/user", 200, "{\"login\":\"aihub-svc\"}");
            stub("GET", "/api/v1/repos/aihub-svc/demo-model", 200,
                    "{\"full_name\":\"aihub-svc/demo-model\",\"html_url\":\"h\",\"clone_url\":\"c\"}");
            stub("GET", "/api/v1/repos/aihub-svc/demo-model/contents/asset.yaml", 404);
            stub("POST", "/api/v1/repos/aihub-svc/demo-model/contents/asset.yaml", 201);
            stub("GET", "/api/v1/repos/aihub-svc/demo-model/contents/README.md", 200, "{\"sha\":\"abc123\"}");
            stub("PUT", "/api/v1/repos/aihub-svc/demo-model/contents/README.md", 200);

            AssetRepositoryRef ref = provisioner().provision(request());

            assertThat(ref.fullName()).isEqualTo("aihub-svc/demo-model");
            assertThat(linesFor("GET /api/v1/user"))
                    .as("用户名下冲突时需要解析令牌所属用户名作为 owner")
                    .hasSize(1);
            assertThat(requestLines).noneMatch(line -> line.contains("%2F") || line.contains("%2f"));
        }

        @Test
        @DisplayName("回查不到既有仓库时按可重试依赖失败处理，不静默成功")
        void unreadableRepoAfterConflictFailsRetryably() {
            stub("POST", "/api/v1/orgs/e4owner/repos", 409);
            // 回查 404（并发删除或权限不足）
            stub("GET", "/api/v1/repos/e4owner/demo-model", 404);

            assertThatThrownBy(() -> provisioner().provision(request()))
                    .isInstanceOf(DependencyException.class)
                    .satisfies(ex -> assertThat(((DependencyException) ex).errorCode().retryable())
                            .as("冲突但仓库不可读必须是可重试错误，而不是伪成功")
                            .isTrue());
        }

        @Test
        @DisplayName("真实依赖不可用时仍上抛可重试错误，未被幂等分支吞掉")
        void unreachableDependencyStillFailsRetryably() throws IOException {
            // 关掉服务端，模拟 Gitea 真实不可用（连接被拒）。
            server.stop(0);

            assertThatThrownBy(() -> provisioner().provision(request()))
                    .isInstanceOf(DependencyException.class)
                    .satisfies(ex -> {
                        ErrorCode code = ((DependencyException) ex).errorCode();
                        assertThat(code).isEqualTo(ErrorCode.ASSET_REPOSITORY_PROVISION_FAILED);
                        assertThat(code.retryable())
                                .as("依赖故障必须保持 retryable，使可靠任务能退避重试")
                                .isTrue();
                    });
        }

        @Test
        @DisplayName("5xx 不会被当成「已存在」而伪成功")
        void serverErrorIsNotTreatedAsExistingRepo() {
            stub("POST", "/api/v1/orgs/e4owner/repos", 503);
            stub("POST", "/api/v1/user/repos", 503);

            assertThatThrownBy(() -> provisioner().provision(request()))
                    .isInstanceOf(DependencyException.class);
            assertThat(linesFor("POST /api/v1/repos/"))
                    .as("依赖 5xx 时不得继续写文件，避免把失败掩盖成部分成功")
                    .isEmpty();
            assertThat(linesFor("PUT ")).isEmpty();
        }
    }

    @Nested
    @DisplayName("调用必须有界：依赖无响应时不得跨越任务租约")
    class BoundedCall {

        @Test
        @DisplayName("Gitea 接受连接但不响应时按可重试依赖失败，且在读取超时内返回")
        void hangingDependencyFailsWithinReadTimeout() {
            // 依赖「无响应」的最危险形态：TCP 可连、永不回包。
            // 若读取超时缺失，该调用会无限期阻塞，跨越 60s 租约后被第二个 Worker 重新领取，
            // 造成同一资产的重复执行与伪并发冲突。
            server.stop(0);
            HttpServer hanging = null;
            try {
                hanging = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                hanging.createContext("/", exchange -> {
                    // 故意不发送任何响应，也不关闭交换。
                });
                hanging.start();
                String hangingUrl = "http://127.0.0.1:" + hanging.getAddress().getPort();

                GiteaProperties bounded = new GiteaProperties(
                        true, hangingUrl, "test-token", "main", 1000, 1500);
                GiteaAssetRepositoryProvisioner boundedProvisioner =
                        new GiteaAssetRepositoryProvisioner(RestClient.builder(), bounded);

                long startNanos = System.nanoTime();
                assertThatThrownBy(() -> boundedProvisioner.provision(request()))
                        .isInstanceOf(DependencyException.class)
                        .satisfies(ex -> assertThat(((DependencyException) ex).errorCode().retryable())
                                .as("依赖无响应必须保持可重试，使可靠任务能退避重试")
                                .isTrue());
                long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;
                assertThat(elapsedMillis)
                        .as("必须在读取超时内返回，否则尝试会跨越租约边界被重复领取")
                        .isLessThan(30_000L);
            } catch (IOException ex) {
                throw new IllegalStateException("failed to start hanging server", ex);
            } finally {
                if (hanging != null) {
                    hanging.stop(0);
                }
            }
        }

        @Test
        @DisplayName("超时配置非法时回落到有限默认值，不产生无界调用")
        void nonPositiveTimeoutsFallBackToFiniteDefaults() {
            GiteaProperties invalid = new GiteaProperties(true, baseUrl, "t", null, 0, -1);

            assertThat(invalid.connectTimeoutMillis()).isPositive();
            assertThat(invalid.readTimeoutMillis()).isPositive();
            assertThat(invalid.defaultBranch()).isEqualTo("main");
        }
    }

    @Nested
    @DisplayName("写入动词：新建走 POST，更新走 PUT 且必须带 sha")
    class FileWriteVerb {

        @Test
        @DisplayName("重跑已开通的仓库：不再新建，全部走 PUT 并携带既有 sha")
        void rerunOnExistingRepositoryUpdatesInsteadOfCreating() {
            // 第二次开通的现场：仓库已存在（409 复用），两个文件都已由上一次尝试写入。
            stub("POST", "/api/v1/orgs/e4owner/repos", 409, "{\"message\":\"repository already exists\"}");
            stub("GET", "/api/v1/repos/e4owner/demo-model", 200,
                    "{\"full_name\":\"e4owner/demo-model\",\"html_url\":\"h\",\"clone_url\":\"c\"}");
            stub("GET", "/api/v1/repos/e4owner/demo-model/contents/asset.yaml", 200, "{\"sha\":\"yaml-sha\"}");
            stub("PUT", "/api/v1/repos/e4owner/demo-model/contents/asset.yaml", 200);
            stub("GET", "/api/v1/repos/e4owner/demo-model/contents/README.md", 200, "{\"sha\":\"abc123\"}");
            stub("PUT", "/api/v1/repos/e4owner/demo-model/contents/README.md", 200);

            AssetRepositoryRef ref = provisioner().provision(request());

            assertThat(ref.fullName()).isEqualTo("e4owner/demo-model");
            assertThat(linesFor("POST /api/v1/repos/e4owner/demo-model/contents/"))
                    .as("文件已存在时不得再走新建：POST 对已存在路径返回 409，会使重试永不收敛")
                    .isEmpty();
            assertThat(linesFor("PUT /api/v1/repos/e4owner/demo-model/contents/")).hasSize(2);
            assertThat(bodyOf("PUT /api/v1/repos/e4owner/demo-model/contents/asset.yaml"))
                    .as("更新必须携带读回的 sha，否则 Gitea 返回 422 [SHA]: Required")
                    .contains("yaml-sha");
            assertThat(bodyOf("PUT /api/v1/repos/e4owner/demo-model/contents/README.md"))
                    .contains("abc123");
        }

        @Test
        @DisplayName("读取 sha 与新建之间文件已被并发写入（POST 409）时回读 sha 改按更新收敛")
        void concurrentCreateConflictFallsBackToUpdate() {
            stub("POST", "/api/v1/orgs/e4owner/repos", 201,
                    "{\"full_name\":\"e4owner/demo-model\",\"html_url\":\"h\",\"clone_url\":\"c\"}");
            // 首次读取时 asset.yaml 尚不存在，读完到新建之间被并发写入。
            stubOnce("GET", "/api/v1/repos/e4owner/demo-model/contents/asset.yaml", 404, "{}");
            stub("GET", "/api/v1/repos/e4owner/demo-model/contents/asset.yaml", 200, "{\"sha\":\"raced-sha\"}");
            stub("POST", "/api/v1/repos/e4owner/demo-model/contents/asset.yaml", 409,
                    "{\"message\":\"file already exists\"}");
            stub("PUT", "/api/v1/repos/e4owner/demo-model/contents/asset.yaml", 200);
            stub("GET", "/api/v1/repos/e4owner/demo-model/contents/README.md", 200, "{\"sha\":\"abc123\"}");
            stub("PUT", "/api/v1/repos/e4owner/demo-model/contents/README.md", 200);

            AssetRepositoryRef ref = provisioner().provision(request());

            assertThat(ref.fullName()).isEqualTo("e4owner/demo-model");
            assertThat(linesFor("POST /api/v1/repos/e4owner/demo-model/contents/asset.yaml"))
                    .as("首次尝试读不到 sha，应走新建")
                    .hasSize(1);
            assertThat(bodyOf("PUT /api/v1/repos/e4owner/demo-model/contents/asset.yaml"))
                    .as("冲突后回读 sha 并改按更新提交，使竞态同样收敛而不是耗尽重试预算")
                    .contains("raced-sha");
        }

        @Test
        @DisplayName("新建冲突且回读不到 sha 时按可重试失败处理，不伪成功")
        void concurrentConflictWithoutReadableShaFailsRetryably() {
            stub("POST", "/api/v1/orgs/e4owner/repos", 201, "{\"full_name\":\"e4owner/demo-model\"}");
            stub("GET", "/api/v1/repos/e4owner/demo-model/contents/asset.yaml", 404);
            stub("POST", "/api/v1/repos/e4owner/demo-model/contents/asset.yaml", 409,
                    "{\"message\":\"file already exists\"}");

            assertThatThrownBy(() -> provisioner().provision(request()))
                    .isInstanceOf(DependencyException.class)
                    .satisfies(ex -> assertThat(((DependencyException) ex).errorCode().retryable())
                            .as("无法解释的写入冲突必须保持可重试，不得吞掉")
                            .isTrue());
            assertThat(linesFor("PUT /api/v1/repos/e4owner/demo-model/contents/")).isEmpty();
        }
    }

    @Nested
    @DisplayName("请求构造细节")
    class RequestShape {

        @Test
        @DisplayName("写文件请求带 branch 与 commit message，且默认分支可配置")
        void writeFileIncludesBranchAndMessage() {
            stubHappyPath();

            provisioner().provision(request());

            String postBody = bodyOf("POST /api/v1/repos/e4owner/demo-model/contents/asset.yaml");
            assertThat(postBody).isNotNull();
            String decoded = URLDecoder.decode(postBody, StandardCharsets.UTF_8);
            assertThat(decoded).contains("main");
            assertThat(decoded).contains("chore: initialize asset.yaml");
        }

        @Test
        @DisplayName("覆盖 README 时携带既有 sha，避免 Gitea 拒绝更新")
        void overwriteReadmeCarriesExistingSha() {
            stubHappyPath();

            provisioner().provision(request());

            String readmePut = bodyOf("PUT /api/v1/repos/e4owner/demo-model/contents/README.md");
            assertThat(readmePut)
                    .as("README 覆盖必须带 sha，否则 Gitea 会因缺少 sha 拒绝更新")
                    .isNotNull()
                    .contains("abc123");
            assertThat(exactLinesFor("GET",
                    "/api/v1/repos/e4owner/demo-model/contents/README.md", "ref=main"))
                    .as("覆盖前需先读取既有 sha")
                    .hasSize(1);
        }

        @Test
        @DisplayName("Token 只出现在请求头，不进入 URL")
        void tokenIsSentAsHeaderNotInUrl() {
            stubHappyPath();

            provisioner().provision(request());

            assertThat(requestLines).noneMatch(line -> line.contains("test-token"));
        }
    }
}
