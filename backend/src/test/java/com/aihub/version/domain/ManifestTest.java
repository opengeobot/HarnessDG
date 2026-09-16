/**
 * 功能: Manifest 规范化与摘要单元测试（含 Golden Vector）
 * 时间: 2026-07-30
 * 作者: AxeXie
 */
package com.aihub.version.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link Manifest} 规范化与摘要单元测试。
 */
class ManifestTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // =========================================================
    // Original unit tests
    // =========================================================

    @Test
    void canonicalizeSortsKeysByUnicodeOrder() {
        Manifest manifest = new Manifest(Map.of(
                "zebra", "1",
                "alpha", "2",
                "middle", "3"));

        String canonical = manifest.canonicalize();
        assertThat(canonical).isEqualTo("alpha=2\nmiddle=3\nzebra=1\n");
    }

    @Test
    void canonicalizeSortsArrayValues() {
        Manifest manifest = new Manifest(Map.of(
                "files", List.of("c.bin", "a.bin", "b.bin")));

        String canonical = manifest.canonicalize();
        assertThat(canonical).isEqualTo("files=a.bin,b.bin,c.bin\n");
    }

    @Test
    void canonicalizeSkipsNullAndBlankValues() {
        Manifest manifest = new Manifest(Map.of(
                "valid", "data",
                "empty", "",
                "blank", "   "));

        String canonical = manifest.canonicalize();
        assertThat(canonical).isEqualTo("valid=data\n");
    }

    @Test
    void canonicalizeSkipsEmptyLists() {
        Manifest manifest = new Manifest(Map.of(
                "valid", "data",
                "empty_list", List.of()));

        String canonical = manifest.canonicalize();
        assertThat(canonical).isEqualTo("valid=data\n");
    }

    @Test
    void computeDigestIsDeterministic() {
        Manifest m1 = new Manifest(Map.of("a", "1", "b", "2"));
        Manifest m2 = new Manifest(Map.of("b", "2", "a", "1"));

        assertThat(m1.computeDigest()).isEqualTo(m2.computeDigest());
    }

    @Test
    void computeDigestDiffersForDifferentContent() {
        Manifest m1 = new Manifest(Map.of("a", "1"));
        Manifest m2 = new Manifest(Map.of("a", "2"));

        assertThat(m1.computeDigest()).isNotEqualTo(m2.computeDigest());
    }

    @Test
    void computeDigestIsSha256Hex() {
        Manifest manifest = new Manifest(Map.of("test", "data"));
        String digest = manifest.computeDigest();
        assertThat(digest).matches("[0-9a-f]{64}");
    }

    // =========================================================
    // Golden Vector tests
    // =========================================================

    @Test
    void goldenVectorBasicManifest() {
        Manifest manifest = loadFixture("basic.json");
        String canonical = manifest.canonicalize();
        String digest = manifest.computeDigest();

        // canonical form:
        // files=README.md,data.csv
        // format=csv
        // license=MIT
        // size_bytes=1048576
        // version=1.0.0
        assertThat(canonical).isEqualTo(
                "files=README.md,data.csv\nformat=csv\nlicense=MIT\n"
                + "size_bytes=1048576\nversion=1.0.0\n");
        assertThat(digest).isEqualTo(
                "8a46786512eb95dd97e8354b833199d813ee79fc6cd65fdebdfbe242945cc0b9");
    }

    @Test
    void goldenVectorEmptyFieldsSkipped() {
        Manifest manifest = loadFixture("empty_fields.json");
        String canonical = manifest.canonicalize();

        // Only "files=data.csv" remains; empty description, null_field, and empty_list are skipped
        assertThat(canonical).isEqualTo("files=data.csv\n");
    }

    @Test
    void goldenVectorMaliciousPathsPreserved() {
        Manifest manifest = loadFixture("malicious_paths.json");
        String canonical = manifest.canonicalize();

        // Manifest normalizes Unicode but does NOT sanitize file paths
        // Arrays are sorted: "../escape.csv", "./relative.csv", "normal.csv"
        assertThat(canonical).contains("../escape.csv");
        assertThat(canonical).contains("./relative.csv");
        assertThat(canonical).contains("normal.csv");
    }

    @Test
    void goldenVectorKeyOrderDoesNotAffectDigest() {
        Manifest manifest = loadFixture("key_order_variants.json");
        String canonical = manifest.canonicalize();

        // Keys must be sorted: a_field, m_field, z_field
        assertThat(canonical).isEqualTo("a_field=first\nm_field=middle\nz_field=last\n");

        // Verify digest is the same regardless of original map insertion order
        Manifest reversedOrder = new Manifest(Map.of(
                "z_field", "last",
                "m_field", "middle",
                "a_field", "first"
        ));
        assertThat(manifest.computeDigest()).isEqualTo(reversedOrder.computeDigest());
    }

    // =========================================================
    // Unicode NFC / NFD tests
    // =========================================================

    @Test
    void goldenVectorUnicodeNfcNfdProduceSameDigest() {
        // Load the NFC fixture (CAF\u00E9 = é as single NFC codepoint)
        Manifest nfcManifest = loadFixture("unicode_nfc_nfd.json");

        // Build the NFD variant manually: CAFé where é = e + combining acute (U+0301)
        String nfdDescription = "CAFe\u0301";
        Manifest nfdManifest = new Manifest(Map.of(
                "description", nfdDescription,
                "files", List.of("data.csv"),
                "license", "MIT"
        ));

        // Both should canonicalize to the same string and produce the same digest
        assertThat(nfcManifest.canonicalize()).isEqualTo(nfdManifest.canonicalize());
        assertThat(nfcManifest.computeDigest()).isEqualTo(nfdManifest.computeDigest());
    }

    @Test
    void unicodeNfcNormalizationAppliedToStringValues() {
        // NFD "e + combining acute" should become NFC "é" after canonicalize
        String nfdValue = "e\u0301";
        Manifest manifest = new Manifest(Map.of("accent", nfdValue));

        String canonical = manifest.canonicalize();
        String expected = Normalizer.normalize("accent", Normalizer.Form.NFC)
                + "=" + Normalizer.normalize(nfdValue, Normalizer.Form.NFC) + "\n";
        assertThat(canonical).isEqualTo(expected);
    }

    @Test
    void unicodeNfcNormalizationAppliedToKeys() {
        // Key with NFD characters should be normalized
        String nfdKey = "descri\u0301ption";
        String nfcKey = Normalizer.normalize(nfdKey, Normalizer.Form.NFC);
        Manifest manifest = new Manifest(Map.of(nfdKey, "value"));

        String canonical = manifest.canonicalize();
        assertThat(canonical).startsWith(nfcKey + "=");
    }

    @Test
    void unicodeNfcNormalizationAppliedToArrayElements() {
        // Array elements should be NFC-normalized
        String nfdFile = "data_e\u0301.csv";
        Manifest manifest = new Manifest(Map.of("files", List.of(nfdFile)));

        String canonical = manifest.canonicalize();
        String expectedNfc = Normalizer.normalize(nfdFile, Normalizer.Form.NFC);
        assertThat(canonical).contains(expectedNfc);
        assertThat(canonical).doesNotContain(nfdFile);
    }

    // =========================================================
    // Helpers
    // =========================================================

    /**
     * Load a golden-vector JSON fixture from classpath and create a Manifest.
     */
    private Manifest loadFixture(String fileName) {
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream("golden-vector/" + fileName)) {
            if (is == null) {
                throw new IllegalArgumentException(
                        "Golden vector fixture not found: golden-vector/" + fileName);
            }
            Map<String, Object> entries = MAPPER.readValue(
                    is, new TypeReference<>() {});
            return new Manifest(entries);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load fixture: " + fileName, e);
        }
    }
}