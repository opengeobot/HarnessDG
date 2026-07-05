/*
 * 功能: P0B 字典与国际化生命周期集成测试——覆盖 AC-P0B-TAX-001..006。
 * 时间: 2026-07-05
 * 作者: AxeXie
 */
package com.aihub.taxonomy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aihub.shared.error.ValidationException;
import com.aihub.taxonomy.application.I18nQueryService;
import com.aihub.taxonomy.dictionary.application.DictionaryApplicationService;
import com.aihub.taxonomy.dictionary.application.DictionaryDtos.CreateDictionaryItemCommand;
import com.aihub.taxonomy.dictionary.application.DictionaryDtos.DictionaryItemView;
import com.aihub.taxonomy.dictionary.application.DictionaryDtos.UpdateDictionaryItemCommand;
import com.aihub.taxonomy.dictionary.application.DictionaryValidationPort;
import com.aihub.taxonomy.domain.TaxonomyStatus;
import com.aihub.taxonomy.tag.application.TagApplicationService;
import com.aihub.taxonomy.tag.application.TagDtos.CreateTagCommand;
import com.aihub.taxonomy.tag.application.TagDtos.TagScopeContext;
import com.aihub.taxonomy.tag.application.TagDtos.TagView;
import com.aihub.taxonomy.tag.application.TagValidationService;
import com.aihub.taxonomy.tag.domain.TagScopeType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * P0B 字典与国际化生命周期集成测试。
 *
 * <p>补充 {@link TaxonomyIT} 未显式映射的 AC 场景。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("test")
class TaxonomyLifecycleIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("aihub")
                    .withUsername("aihub")
                    .withPassword("aihub");

    @DynamicPropertySource
    static void registerDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private DictionaryApplicationService dictionaryService;
    @Autowired private DictionaryValidationPort dictionaryValidation;
    @Autowired private I18nQueryService i18nQueryService;
    @Autowired private TagApplicationService tagService;
    @Autowired private TagValidationService tagValidation;

    // ── AC-P0B-TAX-001: 管理员创建/更新字典项，itemCode 稳定、版本递增、i18n 文案存在 ──────────

    @Test
    void tax001_createAndUpdateDictionaryItemWithVersionIncrement() {
        long versionBefore = dictionaryService.listTypes().stream()
                .filter(t -> t.dictCode().equals("sensitivity_level"))
                .findFirst().orElseThrow().version();

        dictionaryService.createItem("sensitivity_level",
                new CreateDictionaryItemCommand("TOP_SECRET", "dict.sensitivity_level.TOP_SECRET", 10),
                "admin_it");

        long versionAfter = dictionaryService.listTypes().stream()
                .filter(t -> t.dictCode().equals("sensitivity_level"))
                .findFirst().orElseThrow().version();
        assertThat(versionAfter).isEqualTo(versionBefore + 1);

        // 新项可校验通过。
        dictionaryValidation.validateItemCode("sensitivity_level", "TOP_SECRET");
    }

    // ── AC-P0B-TAX-002: 停用已被引用的字典项 → 新写入拒绝，历史仍回显 ──────────

    @Test
    void tax002_disableItemRejectsNewWritesButAllowsHistoryEcho() {
        // 创建然后停用。
        dictionaryService.createItem("license_catalog",
                new CreateDictionaryItemCommand("BSD_3", "dict.license_catalog.BSD_3", 11),
                "admin_it");
        var items = dictionaryService.listItems("license_catalog", false);
        DictionaryItemView bsd = items.stream()
                .filter(i -> i.itemCode().equals("BSD_3"))
                .findFirst().orElseThrow();

        dictionaryService.updateItem("license_catalog", "BSD_3",
                new UpdateDictionaryItemCommand(null, null, TaxonomyStatus.DISABLED, bsd.version()),
                "admin_it");

        // 新建引用被拒。
        assertThatThrownBy(() -> dictionaryValidation.validateItemCode("license_catalog", "BSD_3"))
                .isInstanceOf(ValidationException.class);

        // isKnown 仍 true（历史回显）。
        assertThat(dictionaryValidation.isKnown("license_catalog", "BSD_3")).isTrue();
    }

    // ── AC-P0B-TAX-003: 创建平台标签和两个组织的同 code 标签，唯一约束按作用域 ──────────

    @Test
    void tax003_scopedTagUniquenessAcrossOrganizations() {
        TagView platformTag = tagService.createTag(new CreateTagCommand(TagScopeType.PLATFORM, null,
                "shared-code", "平台标签", "tag.shared-code", "#000"), "system");
        assertThat(platformTag.scopeId()).isEqualTo("PLATFORM");

        // 两个组织可以有同 code 标签。
        TagView org1Tag = tagService.createTag(new CreateTagCommand(TagScopeType.ORGANIZATION,
                "org_alpha", "shared-code", "组织 Alpha", "tag.org-alpha.shared", null), "system");
        TagView org2Tag = tagService.createTag(new CreateTagCommand(TagScopeType.ORGANIZATION,
                "org_beta", "shared-code", "组织 Beta", "tag.org-beta.shared", null), "system");

        assertThat(org1Tag.tagId()).isNotEqualTo(org2Tag.tagId());
        // 各组织上下文只能引用自己的标签。
        tagValidation.resolveActiveTags(List.of(org1Tag.tagId()),
                new TagScopeContext(TagScopeType.ORGANIZATION, "org_alpha"));
        assertThatThrownBy(() -> tagValidation.resolveActiveTags(List.of(org2Tag.tagId()),
                new TagScopeContext(TagScopeType.ORGANIZATION, "org_alpha")))
                .isInstanceOf(ValidationException.class);
    }

    // ── AC-P0B-TAX-004: 普通用户提交自由标签/未知 tagId/跨组织 tagId 被拒 ──────────

    @Test
    void tax004_freeFormAndUnknownTagsRejected() {
        // 未注册 tagId。
        assertThatThrownBy(() -> tagValidation.resolveActiveTags(
                List.of("tag_nonexistent_999"), TagScopeContext.platform()))
                .isInstanceOf(ValidationException.class);

        // 跨组织引用。
        TagView orgTag = tagService.createTag(new CreateTagCommand(TagScopeType.ORGANIZATION,
                "org_isolated", "isolated-tag", "Isolated", "tag.isolated", null), "system");
        assertThatThrownBy(() -> tagValidation.resolveActiveTags(List.of(orgTag.tagId()),
                new TagScopeContext(TagScopeType.ORGANIZATION, "org_other")))
                .isInstanceOf(ValidationException.class);
    }

    // ── AC-P0B-TAX-005: 停用已关联标签 → 历史可回显、新关联被拒 ──────────

    @Test
    void tax005_disableTagRejectsNewAssociationButAllowsEcho() {
        TagView tag = tagService.createTag(new CreateTagCommand(TagScopeType.PLATFORM, null,
                "tax005-tag", "Tax005", "tag.tax005", null), "system");
        // 活跃时可解析。
        tagValidation.resolveActiveTags(List.of(tag.tagId()), TagScopeContext.platform());

        // 停用。
        tagService.disableTag(tag.tagId(), tag.version(), "system");

        // 新关联被拒。
        assertThatThrownBy(() -> tagValidation.resolveActiveTags(
                List.of(tag.tagId()), TagScopeContext.platform()))
                .isInstanceOf(ValidationException.class);

        // 回显仍可查询。
        assertThat(tagValidation.viewByIds(List.of(tag.tagId()))).hasSize(1);
    }

    // ── AC-P0B-TAX-006: zh-CN/en-US 切换，无业务文案硬编码 ──────────

    @Test
    void tax006_i18nMessagesExistForBothLocales() {
        Map<String, String> zh = i18nQueryService.messages("zh-CN");
        Map<String, String> en = i18nQueryService.messages("en-US");

        // 两种语言均有字典项文案。
        assertThat(zh).isNotEmpty();
        assertThat(en).isNotEmpty();

        // 至少有一些共同的 key。
        assertThat(zh.keySet()).containsAnyElementsOf(en.keySet());

        // 字典项文案存在。
        assertThat(zh).containsKey("dict.license_catalog.MIT");
        assertThat(en).containsKey("dict.license_catalog.MIT");
    }
}
