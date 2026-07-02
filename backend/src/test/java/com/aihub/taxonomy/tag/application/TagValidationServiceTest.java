/*
 * 功能: 受控标签校验服务单元测试——resolveActiveTags 拒绝未登记/停用/跨作用域标签，viewByIds 含停用回显。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.taxonomy.tag.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.error.ValidationException;
import com.aihub.taxonomy.domain.TaxonomyStatus;
import com.aihub.taxonomy.tag.application.TagDtos.TagScopeContext;
import com.aihub.taxonomy.tag.application.TagDtos.TagView;
import com.aihub.taxonomy.tag.domain.Tag;
import com.aihub.taxonomy.tag.domain.TagRepository;
import com.aihub.taxonomy.tag.domain.TagScopeType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 受控标签校验服务单元测试。
 */
@ExtendWith(MockitoExtension.class)
class TagValidationServiceTest {

    @Mock
    private TagRepository repository;

    private TagValidationService service;

    @BeforeEach
    void setUp() {
        service = new TagValidationService(repository);
    }

    private Tag platformTag(String id, String code, TaxonomyStatus status, long version) {
        return new Tag(id, TagScopeType.PLATFORM, "PLATFORM", code, code,
                "tag." + code, null, status, "admin", version);
    }

    private Tag orgTag(String id, String orgId, String code, TaxonomyStatus status) {
        return new Tag(id, TagScopeType.ORGANIZATION, orgId, code, code,
                "tag." + code, null, status, "admin", 1L);
    }

    @Test
    void resolveActiveTagsPassesForActivePlatformTagsInPlatformContext() {
        Tag t1 = platformTag("tag_1", "vision", TaxonomyStatus.ACTIVE, 1L);
        Tag t2 = platformTag("tag_2", "nlp", TaxonomyStatus.ACTIVE, 1L);
        given(repository.findByTagIds(List.of("tag_1", "tag_2"))).willReturn(List.of(t1, t2));
        List<TagView> views = service.resolveActiveTags(
                List.of("tag_1", "tag_2"), TagScopeContext.platform());
        assertThat(views).hasSize(2);
    }

    @Test
    void resolveActiveTagsRejectsUnregisteredTagId() {
        given(repository.findByTagIds(List.of("tag_x"))).willReturn(List.of());
        assertThatThrownBy(() -> service.resolveActiveTags(
                List.of("tag_x"), TagScopeContext.platform()))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).errorCode())
                        .isEqualTo(ErrorCode.TAG_VALUE_INVALID));
    }

    @Test
    void resolveActiveTagsRejectsDisabledTagForNewAssociation() {
        Tag disabled = platformTag("tag_1", "vision", TaxonomyStatus.DISABLED, 1L);
        given(repository.findByTagIds(List.of("tag_1"))).willReturn(List.of(disabled));
        assertThatThrownBy(() -> service.resolveActiveTags(
                List.of("tag_1"), TagScopeContext.platform()))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).errorCode())
                        .isEqualTo(ErrorCode.TAG_VALUE_INVALID));
    }

    @Test
    void resolveActiveTagsRejectsOtherOrganizationTag() {
        Tag ownOrg = orgTag("tag_1", "org_a", "vision", TaxonomyStatus.ACTIVE);
        Tag otherOrg = orgTag("tag_2", "org_b", "nlp", TaxonomyStatus.ACTIVE);
        given(repository.findByTagIds(List.of("tag_1", "tag_2")))
                .willReturn(List.of(ownOrg, otherOrg));
        assertThatThrownBy(() -> service.resolveActiveTags(
                List.of("tag_1", "tag_2"), new TagScopeContext(TagScopeType.ORGANIZATION, "org_a")))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void resolveActiveTagsAllowsPlatformTagInOrganizationContext() {
        Tag platform = platformTag("tag_1", "vision", TaxonomyStatus.ACTIVE, 1L);
        Tag ownOrg = orgTag("tag_2", "org_a", "nlp", TaxonomyStatus.ACTIVE);
        given(repository.findByTagIds(List.of("tag_1", "tag_2")))
                .willReturn(List.of(platform, ownOrg));
        List<TagView> views = service.resolveActiveTags(
                List.of("tag_1", "tag_2"), new TagScopeContext(TagScopeType.ORGANIZATION, "org_a"));
        assertThat(views).hasSize(2);
    }

    @Test
    void viewByIdsIncludesDisabledForDisplayback() {
        Tag disabled = platformTag("tag_1", "vision", TaxonomyStatus.DISABLED, 1L);
        given(repository.findByTagIds(List.of("tag_1", "tag_missing"))).willReturn(List.of(disabled));
        List<TagView> views = service.viewByIds(List.of("tag_1", "tag_missing"));
        assertThat(views).hasSize(1);
        assertThat(views.get(0).status()).isEqualTo(TaxonomyStatus.DISABLED);
    }

    @Test
    void resolveActiveTagsRejectsNullScopeContext() {
        assertThatThrownBy(() -> service.resolveActiveTags(List.of("tag_1"), null))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void resolveActiveTagsEmptyInputReturnsEmpty() {
        assertThat(service.resolveActiveTags(List.of(), TagScopeContext.platform())).isEmpty();
    }
}
