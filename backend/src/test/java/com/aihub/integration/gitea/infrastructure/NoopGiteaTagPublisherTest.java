package com.aihub.integration.gitea.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.aihub.integration.gitea.domain.GiteaTagPublisher.TagPublishMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("NoopGiteaTagPublisher")
class NoopGiteaTagPublisherTest {

    @Test
    @DisplayName("Gitea 禁用时应返回 PG_ONLY 模式")
    void shouldReturnPgOnlyMode() {
        var publisher = new NoopGiteaTagPublisher();
        var result = publisher.createProtectedTag("org/model-a", "v1.0.0",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        assertThat(result.mode()).isEqualTo(TagPublishMode.PG_ONLY_GITEA_DISABLED);
        assertThat(result.tagName()).isEqualTo("v1.0.0");
    }
}
