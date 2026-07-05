package com.aihub.version.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * {@link Version} 领域单元测试。
 */
class VersionTest {

    @Test
    void createDraftReturnsVersionWithDraftStatus() {
        Version v = Version.createDraft("ver_01", "ast_01", "v1.0.0", "usr_01");

        assertThat(v.versionId()).isEqualTo("ver_01");
        assertThat(v.status()).isEqualTo(VersionStatus.DRAFT);
        assertThat(v.sourceCommit()).isNull();
        assertThat(v.manifestDigest()).isNull();
    }

    @Test
    void transitionFromDraftToValidatingSucceeds() {
        Version v = Version.createDraft("ver_01", "ast_01", "v1.0.0", "usr_01");
        v.transitionTo(VersionStatus.VALIDATING);
        assertThat(v.status()).isEqualTo(VersionStatus.VALIDATING);
    }

    @Test
    void transitionFromDraftToPublishedThrowsIllegalState() {
        Version v = Version.createDraft("ver_01", "ast_01", "v1.0.0", "usr_01");
        assertThatThrownBy(() -> v.transitionTo(VersionStatus.PUBLISHED))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void publishFromPendingReviewSucceeds() {
        Version v = Version.createDraft("ver_01", "ast_01", "v1.0.0", "usr_01");
        v.transitionTo(VersionStatus.VALIDATING);
        v.transitionTo(VersionStatus.PENDING_REVIEW);
        v.publish("sha256digest", "v1.0.0", "usr_01");

        assertThat(v.status()).isEqualTo(VersionStatus.PUBLISHED);
        assertThat(v.manifestDigest()).isEqualTo("sha256digest");
        assertThat(v.gitTag()).isEqualTo("v1.0.0");
        assertThat(v.publishedAt()).isNotNull();
    }

    @Test
    void publishFromDraftThrowsIllegalState() {
        Version v = Version.createDraft("ver_01", "ast_01", "v1.0.0", "usr_01");
        assertThatThrownBy(() -> v.publish("digest", "tag", "usr_01"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void bindManifestDigestInDraftSucceeds() {
        Version v = Version.createDraft("ver_01", "ast_01", "v1.0.0", "usr_01");
        v.bindManifestDigest("sha256abc");
        assertThat(v.manifestDigest()).isEqualTo("sha256abc");
    }

    @Test
    void bindManifestDigestInPublishedThrowsIllegalState() {
        Version v = Version.createDraft("ver_01", "ast_01", "v1.0.0", "usr_01");
        v.transitionTo(VersionStatus.VALIDATING);
        v.transitionTo(VersionStatus.PENDING_REVIEW);
        v.publish("digest", "v1.0.0", "usr_01");

        assertThatThrownBy(() -> v.bindManifestDigest("newdigest"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deprecatedVersionCanBeRestoredToPublished() {
        Version v = Version.createDraft("ver_01", "ast_01", "v1.0.0", "usr_01");
        v.transitionTo(VersionStatus.VALIDATING);
        v.transitionTo(VersionStatus.PENDING_REVIEW);
        v.publish("digest", "v1.0.0", "usr_01");
        v.transitionTo(VersionStatus.DEPRECATED);
        v.transitionTo(VersionStatus.PUBLISHED);

        assertThat(v.status()).isEqualTo(VersionStatus.PUBLISHED);
    }

    @Test
    void archivedVersionCannotTransitionAnywhere() {
        Version v = Version.createDraft("ver_01", "ast_01", "v1.0.0", "usr_01");
        v.transitionTo(VersionStatus.ARCHIVED);

        assertThatThrownBy(() -> v.transitionTo(VersionStatus.PUBLISHED))
                .isInstanceOf(IllegalStateException.class);
    }
}
