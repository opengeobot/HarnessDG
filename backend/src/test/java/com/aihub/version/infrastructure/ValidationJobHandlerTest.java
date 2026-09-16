/*
 * 功能: ValidationJobHandler 单元测试，覆盖 Pickle 高风险文件检测（REQ-VAL-001）。
 * 时间: 2026-07-30
 * 作者: AxeXie
 */
package com.aihub.version.infrastructure;

import com.aihub.job.domain.JobContext;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import com.aihub.version.domain.Artifact;
import com.aihub.version.domain.Version;
import com.aihub.version.domain.VersionRepository;
import com.aihub.version.domain.VersionStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ValidationJobHandler")
class ValidationJobHandlerTest {

    @Mock private VersionRepository versionRepository;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private IdGenerator idGenerator;

    private ValidationJobHandler handler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        handler = new ValidationJobHandler(versionRepository, jdbcTemplate, idGenerator, objectMapper);
        lenient().when(idGenerator.generate(IdPrefix.VALIDATION_REPORT)).thenReturn("vrp_01");
        lenient().when(jdbcTemplate.update(anyString(), any(Object.class))).thenReturn(1);
        lenient().when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any())).thenReturn(1);
    }

    // ---- 基础校验 ----

    @Test
    @DisplayName("应返回正确的任务类型")
    void shouldReturnCorrectType() {
        assertThat(handler.type()).isEqualTo("VERSION_VALIDATE");
    }

    @Test
    @DisplayName("版本不存在时应跳过校验")
    void shouldSkipWhenVersionNotFound() throws Exception {
        when(versionRepository.findByVersionId("ver_missing")).thenReturn(Optional.empty());

        JobContext ctx = buildContext("ver_missing");
        handler.handle(ctx);

        verify(jdbcTemplate, org.mockito.Mockito.never())
                .update(anyString(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("版本状态非 VALIDATING 时应跳过校验")
    void shouldSkipWhenNotValidating() throws Exception {
        Version v = Version.createDraft("ver_01", "ast_01", "v1.0.0", "usr_01");
        when(versionRepository.findByVersionId("ver_01")).thenReturn(Optional.of(v));

        JobContext ctx = buildContext("ver_01");
        handler.handle(ctx);

        verify(jdbcTemplate, org.mockito.Mockito.never())
                .update(anyString(), any(), any(), any(), any(), any(), any());
    }

    // ---- Pickle 高风险文件检测 ----

    @Test
    @DisplayName("artifacts 包含 .pkl 文件时 security check 应返回 RISK_HIGH")
    void pklArtifactShouldTriggerRiskHigh() throws Exception {
        Version v = Version.createDraft("ver_01", "ast_01", "v1.0.0", "usr_01");
        v.transitionTo(VersionStatus.VALIDATING);
        v.bindManifestDigest("sha256abc");
        when(versionRepository.findByVersionId("ver_01")).thenReturn(Optional.of(v));

        Artifact risky = new Artifact("art_01", "ver_01", "models/model.pkl",
                null, "dvc_hash_1", "sha_01", 1024, "application/octet-stream");
        when(versionRepository.listArtifactsByVersion("ver_01")).thenReturn(List.of(risky));

        JobContext ctx = buildContext("ver_01");
        handler.handle(ctx);

        // 校验 security finding 为 RISK_HIGH
        verify(jdbcTemplate).update(
                org.mockito.ArgumentMatchers.argThat(sql -> sql.contains("INSERT INTO validation_report")),
                anyString(), anyString(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.argThat(findingsJson -> {
                    try {
                        JsonNode findings = objectMapper.readTree((String) findingsJson);
                        boolean foundRisk = false;
                        for (JsonNode f : findings) {
                            if ("security".equals(f.path("check").asText())
                                    && "RISK_HIGH".equals(f.path("status").asText())) {
                                foundRisk = true;
                            }
                        }
                        return foundRisk;
                    } catch (Exception e) {
                        return false;
                    }
                }),
                any());
    }

    @Test
    @DisplayName("artifacts 仅包含 .csv/.parquet 文件时 security check 应返回 PASSED")
    void safeArtifactsShouldPassSecurityCheck() throws Exception {
        Version v = Version.createDraft("ver_01", "ast_01", "v1.0.0", "usr_01");
        v.transitionTo(VersionStatus.VALIDATING);
        v.bindManifestDigest("sha256abc");
        when(versionRepository.findByVersionId("ver_01")).thenReturn(Optional.of(v));

        Artifact csv = new Artifact("art_01", "ver_01", "data/train.csv",
                null, "dvc_hash_1", "sha_01", 2048, "text/csv");
        Artifact parquet = new Artifact("art_02", "ver_01", "data/test.parquet",
                null, "dvc_hash_2", "sha_02", 4096, "application/octet-stream");
        when(versionRepository.listArtifactsByVersion("ver_01")).thenReturn(List.of(csv, parquet));

        JobContext ctx = buildContext("ver_01");
        handler.handle(ctx);

        verify(jdbcTemplate).update(
                org.mockito.ArgumentMatchers.argThat(sql -> sql.contains("INSERT INTO validation_report")),
                anyString(), anyString(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.argThat(findingsJson -> {
                    try {
                        JsonNode findings = objectMapper.readTree((String) findingsJson);
                        boolean foundPassed = false;
                        for (JsonNode f : findings) {
                            if ("security".equals(f.path("check").asText())
                                    && "PASSED".equals(f.path("status").asText())) {
                                foundPassed = true;
                            }
                        }
                        return foundPassed;
                    } catch (Exception e) {
                        return false;
                    }
                }),
                any());
    }

    @Test
    @DisplayName(".joblib/.h5/.pt 文件均应触发 RISK_HIGH")
    void mlModelArtifactsShouldTriggerRiskHigh() throws Exception {
        Version v = Version.createDraft("ver_01", "ast_01", "v1.0.0", "usr_01");
        v.transitionTo(VersionStatus.VALIDATING);
        v.bindManifestDigest("sha256abc");
        when(versionRepository.findByVersionId("ver_01")).thenReturn(Optional.of(v));

        Artifact joblib = new Artifact("art_01", "ver_01", "models/clf.joblib",
                null, "dvc_1", "sha_01", 100, "application/octet-stream");
        Artifact h5 = new Artifact("art_02", "ver_01", "models/nn.h5",
                null, "dvc_2", "sha_02", 200, "application/octet-stream");
        Artifact pt = new Artifact("art_03", "ver_01", "models/checkpoint.pt",
                null, "dvc_3", "sha_03", 300, "application/octet-stream");
        when(versionRepository.listArtifactsByVersion("ver_01")).thenReturn(List.of(joblib, h5, pt));

        JobContext ctx = buildContext("ver_01");
        handler.handle(ctx);

        verify(jdbcTemplate).update(
                org.mockito.ArgumentMatchers.argThat(sql -> sql.contains("INSERT INTO validation_report")),
                anyString(), anyString(), anyString(), anyString(),
                org.mockito.ArgumentMatchers.argThat(findingsJson -> {
                    try {
                        JsonNode findings = objectMapper.readTree((String) findingsJson);
                        boolean foundRisk = false;
                        for (JsonNode f : findings) {
                            if ("security".equals(f.path("check").asText())
                                    && "RISK_HIGH".equals(f.path("status").asText())) {
                                foundRisk = true;
                            }
                        }
                        return foundRisk;
                    } catch (Exception e) {
                        return false;
                    }
                }),
                any());
    }

    @Test
    @DisplayName("RISK_HIGH 不应阻止校验通过（allPassed 仍为 true）")
    void riskHighShouldNotBlockValidation() throws Exception {
        Version v = Version.createDraft("ver_01", "ast_01", "v1.0.0", "usr_01");
        v.transitionTo(VersionStatus.VALIDATING);
        v.bindManifestDigest("sha256abc");
        when(versionRepository.findByVersionId("ver_01")).thenReturn(Optional.of(v));

        Artifact risky = new Artifact("art_01", "ver_01", "models/model.pkl",
                null, "dvc_1", "sha_01", 100, "application/octet-stream");
        when(versionRepository.listArtifactsByVersion("ver_01")).thenReturn(List.of(risky));

        JobContext ctx = buildContext("ver_01");
        handler.handle(ctx);

        // 校验状态推进到 PENDING_REVIEW（而非回退到 DRAFT）
        verify(versionRepository).update(org.mockito.ArgumentMatchers.argThat(
                ver -> ver.status() == VersionStatus.PENDING_REVIEW));
    }

    // ---- 辅助方法 ----

    private JobContext buildContext(String versionId) throws Exception {
        Map<String, Object> payload = Map.of("versionId", versionId);
        return new JobContext("job_01", "VERSION_VALIDATE",
                objectMapper.writeValueAsString(payload), 0, "usr_01", "trace_01", "ast_01");
    }
}