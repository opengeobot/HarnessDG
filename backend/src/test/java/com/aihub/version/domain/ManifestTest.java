/*
 * 功能: Manifest 黄金 fixture 摘要校验测试。
 * 时间: 2026-07-10
 * 作者: AxeXie
 */
package com.aihub.version.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("Manifest golden fixtures")
class ManifestTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path FIXTURES_DIR = Path.of("../contracts/fixtures/manifest");

    static Stream<Path> fixtureFiles() throws IOException {
        if (!Files.isDirectory(FIXTURES_DIR)) {
            return Stream.of();
        }
        return Files.list(FIXTURES_DIR)
                .filter(p -> p.toString().endsWith(".json"))
                .sorted();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtureFiles")
    @DisplayName("fixture digest 应与 sidecar 一致")
    void fixtureDigestMatchesSidecar(Path fixturePath) throws Exception {
        Map<String, Object> entries = MAPPER.readValue(fixturePath.toFile(), new TypeReference<>() {});
        String assetId = (String) entries.get("assetId");
        String versionId = (String) entries.get("versionId");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> artifactMaps = (List<Map<String, Object>>) entries.get("artifacts");

        List<Manifest.ArtifactEntry> artifacts = artifactMaps.stream()
                .map(m -> new Manifest.ArtifactEntry(
                        (String) m.get("path"),
                        (String) m.get("sha256"),
                        ((Number) m.get("size")).longValue(),
                        (String) m.get("mediaType")))
                .toList();

        Manifest manifest = Manifest.forVersion(assetId, versionId, artifacts);
        String digest = manifest.computeDigest();

        Path digestPath = Path.of(fixturePath.toString().replace(".json", ".digest"));
        if (Files.exists(digestPath)) {
            String expected = Files.readString(digestPath).trim();
            assertThat(digest).isEqualTo(expected);
        } else {
            // 首次运行写入 sidecar，便于固化黄金值
            Files.writeString(digestPath, digest);
            assertThat(digest).isNotBlank();
        }
    }

    @org.junit.jupiter.api.Test
    @DisplayName("canonicalize 应排除 generatedAt")
    void canonicalizeExcludesGeneratedAt() {
        Manifest manifest = new Manifest(Map.of(
                "schemaVersion", Manifest.SCHEMA_VERSION,
                "assetId", "ast_1",
                "versionId", "ver_1",
                "generatedAt", "2026-01-01T00:00:00Z",
                "artifacts", List.of()));
        assertThat(manifest.canonicalize()).doesNotContain("generatedAt");
    }
}
