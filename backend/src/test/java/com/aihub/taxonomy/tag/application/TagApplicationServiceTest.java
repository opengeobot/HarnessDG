/*
 * 功能: 受控标签应用服务单元测试——作用域解析、(scope,code) 唯一冲突、编码校验、停用/启用。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.taxonomy.tag.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.aihub.shared.error.ConflictException;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.error.NotFoundException;
import com.aihub.shared.error.ValidationException;
import com.aihub.shared.id.IdGenerator;
import com.aihub.taxonomy.application.AuditPort;
import com.aihub.taxonomy.domain.TaxonomyStatus;
import com.aihub.taxonomy.tag.application.TagDtos.CreateTagCommand;
import com.aihub.taxonomy.tag.application.TagDtos.UpdateTagCommand;
import com.aihub.taxonomy.tag.domain.Tag;
import com.aihub.taxonomy.tag.domain.TagRepository;
import com.aihub.taxonomy.tag.domain.TagScopeType;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 受控标签应用服务单元测试。
 */
@ExtendWith(MockitoExtension.class)
class TagApplicationServiceTest {

    @Mock
    private TagRepository repository;
    @Mock
    private IdGenerator idGenerator;
    @Mock
    private AuditPort auditPort;

    private TagApplicationService service;

    @BeforeEach
    void setUp() {
        service = new TagApplicationService(repository, idGenerator, auditPort);
    }

    @Test
    void createPlatformTagNormalizesScopeId() {
        given(repository.existsByCode(TagScopeType.PLATFORM, "PLATFORM", "vision")).willReturn(false);
        given(idGenerator.generate(com.aihub.shared.id.IdPrefix.TAG)).willReturn("tag_1");
        var view = service.createTag(new CreateTagCommand(TagScopeType.PLATFORM, null,
                "vision", "视觉", "tag.vision", "#1677ff"), "admin");
        assertThat(view.scopeId()).isEqualTo("PLATFORM");
        assertThat(view.tagId()).isEqualTo("tag_1");
        assertThat(view.version()).isEqualTo(1L);
    }

    @Test
    void createOrganizationTagRequiresRealScopeId() {
        assertThatThrownBy(() -> service.createTag(new CreateTagCommand(TagScopeType.ORGANIZATION,
                null, "vision", "视觉", "tag.vision", null), "admin"))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.createTag(new CreateTagCommand(TagScopeType.ORGANIZATION,
                "PLATFORM", "vision", "视觉", "tag.vision", null), "admin"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void createTagConflictsOnDuplicateScopeCode() {
        given(repository.existsByCode(TagScopeType.PLATFORM, "PLATFORM", "vision")).willReturn(true);
        assertThatThrownBy(() -> service.createTag(new CreateTagCommand(TagScopeType.PLATFORM, null,
                "vision", "视觉", "tag.vision", null), "admin"))
                .isInstanceOf(ConflictException.class)
                .satisfies(ex -> assertThat(((ConflictException) ex).errorCode())
                        .isEqualTo(ErrorCode.TAG_ALREADY_EXISTS));
    }

    @Test
    void createTagRejectsInvalidTagCode() {
        assertThatThrownBy(() -> service.createTag(new CreateTagCommand(TagScopeType.PLATFORM, null,
                "Bad Code", "视觉", "tag.vision", null), "admin"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void disableTagKeepsHistoryAndIncrementsVersion() {
        Tag existing = new Tag("tag_1", TagScopeType.PLATFORM, "PLATFORM", "vision",
                "视觉", "tag.vision", null, TaxonomyStatus.ACTIVE, "admin", 1L);
        given(repository.findByTagId("tag_1")).willReturn(Optional.of(existing));
        service.disableTag("tag_1", 1L, "admin");
        verify(repository).updateStatus("tag_1", TaxonomyStatus.DISABLED, 1L);
    }

    @Test
    void enableTagNotFound() {
        given(repository.findByTagId("tag_x")).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.enableTag("tag_x", 1L, "admin"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateTagRejectsVersionMismatch() {
        Tag existing = new Tag("tag_1", TagScopeType.PLATFORM, "PLATFORM", "vision",
                "视觉", "tag.vision", null, TaxonomyStatus.ACTIVE, "admin", 5L);
        given(repository.findByTagId("tag_1")).willReturn(Optional.of(existing));
        assertThatThrownBy(() -> service.updateTag("tag_1",
                new UpdateTagCommand("新名", null, null, 1L), "admin"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void updateTagSucceeds() {
        Tag existing = new Tag("tag_1", TagScopeType.PLATFORM, "PLATFORM", "vision",
                "视觉", "tag.vision", null, TaxonomyStatus.ACTIVE, "admin", 1L);
        given(repository.findByTagId("tag_1")).willReturn(Optional.of(existing));
        var view = service.updateTag("tag_1",
                new UpdateTagCommand("新视觉", null, "#000", 1L), "admin");
        assertThat(view.displayName()).isEqualTo("新视觉");
        assertThat(view.color()).isEqualTo("#000");
        assertThat(view.version()).isEqualTo(2L);
        verify(repository).update(any(Tag.class), org.mockito.ArgumentMatchers.eq(1L));
    }
}
