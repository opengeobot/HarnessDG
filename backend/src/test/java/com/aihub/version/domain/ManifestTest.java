package com.aihub.version.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link Manifest} 规范化与摘要单元测试。
 */
class ManifestTest {

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
}
