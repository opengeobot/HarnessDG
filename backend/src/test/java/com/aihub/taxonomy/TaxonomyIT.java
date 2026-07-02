/*
 * 功能: taxonomy 集成测试——以 Testcontainers PostgreSQL 验证 V6/V7 迁移、预置字典/标签数据、
 *       DictionaryValidationPort 治理字段校验、字典变更版本自增、TagValidationService 拒绝自由/停用标签。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.taxonomy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aihub.shared.error.ConflictException;
import com.aihub.shared.error.ValidationException;
import com.aihub.taxonomy.application.I18nQueryService;
import com.aihub.taxonomy.dictionary.application.DictionaryApplicationService;
import com.aihub.taxonomy.dictionary.application.DictionaryDtos.CreateDictionaryItemCommand;
import com.aihub.taxonomy.dictionary.application.DictionaryDtos.DictionaryTypeView;
import com.aihub.taxonomy.dictionary.application.DictionaryValidationPort;
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
 * taxonomy 集成测试。无 Docker 时整体跳过，不阻断 verify。
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("test")
class TaxonomyIT {

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

    @Autowired
    private DictionaryApplicationService dictionaryService;
    @Autowired
    private DictionaryValidationPort dictionaryValidation;
    @Autowired
    private I18nQueryService i18nQueryService;
    @Autowired
    private TagApplicationService tagService;
    @Autowired
    private TagValidationService tagValidation;

    @Test
    void v6MigrationSeedsDictionariesAndItems() {
        // 预置字典类型存在。
        List<DictionaryTypeView> types = dictionaryService.listTypes();
        assertThat(types).extracting(DictionaryTypeView::dictCode)
                .contains("license_catalog", "model_framework", "sensitivity_level");
        // 预置字典项存在且含 i18n_key（不存文案）。
        var items = dictionaryService.listItems("license_catalog", true);
        assertThat(items).extracting(i -> i.itemCode())
                .contains("APACHE_2_0", "MIT", "CC_BY_4_0");
        // 国际化文案已预置。
        Map<String, String> zh = i18nQueryService.messages("zh-CN");
        assertThat(zh).containsKey("dict.license_catalog.MIT");
        assertThat(zh.get("dict.license_catalog.MIT")).contains("MIT");
    }

    @Test
    void dictionaryValidationPortAcceptsActiveRejectsUnknownAndDisabled() {
        // 预置 ACTIVE 项通过。
        dictionaryValidation.validateItemCode("license_catalog", "MIT");
        assertThat(dictionaryValidation.isKnown("license_catalog", "MIT")).isTrue();

        // 未知项拒绝新建引用。
        assertThatThrownBy(() -> dictionaryValidation.validateItemCode("license_catalog", "NOPE"))
                .isInstanceOf(ValidationException.class);
        assertThat(dictionaryValidation.isKnown("license_catalog", "NOPE")).isFalse();

        // 停用项：不可新建引用，但 isKnown 仍 true（回显）。
        dictionaryService.updateItem("license_catalog", "MIT",
                new com.aihub.taxonomy.dictionary.application.DictionaryDtos.UpdateDictionaryItemCommand(
                        null, null, com.aihub.taxonomy.domain.TaxonomyStatus.DISABLED, 1L),
                "system");
        assertThat(dictionaryValidation.isKnown("license_catalog", "MIT")).isTrue();
        assertThatThrownBy(() -> dictionaryValidation.validateItemCode("license_catalog", "MIT"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void dictionaryItemCreateIncrementsTypeCacheVersion() {
        long versionBefore = dictionaryService.listTypes().stream()
                .filter(t -> t.dictCode().equals("model_framework"))
                .findFirst().orElseThrow().version();
        dictionaryService.createItem("model_framework",
                new CreateDictionaryItemCommand("JAX", "dict.model_framework.JAX", 9), "system");
        long versionAfter = dictionaryService.listTypes().stream()
                .filter(t -> t.dictCode().equals("model_framework"))
                .findFirst().orElseThrow().version();
        assertThat(versionAfter).isEqualTo(versionBefore + 1);
    }

    @Test
    void v7TagLifecycleAndValidation() {
        // 创建平台标签。
        TagView tag = tagService.createTag(new CreateTagCommand(TagScopeType.PLATFORM, null,
                "it-vision", "视觉", "tag.it-vision", "#1677ff"), "system");
        assertThat(tag.tagId()).startsWith("tag_");
        assertThat(tag.scopeId()).isEqualTo("PLATFORM");

        // resolveActiveTags 通过；viewByIds 回显。
        List<TagView> resolved = tagValidation.resolveActiveTags(
                List.of(tag.tagId()), TagScopeContext.platform());
        assertThat(resolved).hasSize(1);

        // (scope, code) 唯一冲突。
        assertThatThrownBy(() -> tagService.createTag(new CreateTagCommand(TagScopeType.PLATFORM, null,
                "it-vision", "视觉2", "tag.it-vision2", null), "system"))
                .isInstanceOf(ConflictException.class);

        // 停用后：resolveActiveTags 拒绝（不可新建关联），viewByIds 仍回显。
        tagService.disableTag(tag.tagId(), 1L, "system");
        assertThatThrownBy(() -> tagValidation.resolveActiveTags(
                List.of(tag.tagId()), TagScopeContext.platform()))
                .isInstanceOf(ValidationException.class);
        assertThat(tagValidation.viewByIds(List.of(tag.tagId()))).hasSize(1);

        // 未登记 tagId 被拒（拒绝自由标签）。
        assertThatThrownBy(() -> tagValidation.resolveActiveTags(
                List.of("tag_unregistered"), TagScopeContext.platform()))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void organizationScopedTagIsolation() {
        TagView orgTag = tagService.createTag(new CreateTagCommand(TagScopeType.ORGANIZATION,
                "org_it", "org-only", "组织标签", "tag.org-only", null), "system");
        // 本组织上下文可引用。
        tagValidation.resolveActiveTags(List.of(orgTag.tagId()),
                new TagScopeContext(TagScopeType.ORGANIZATION, "org_it"));
        // 其他组织上下文拒绝。
        assertThatThrownBy(() -> tagValidation.resolveActiveTags(List.of(orgTag.tagId()),
                new TagScopeContext(TagScopeType.ORGANIZATION, "org_other")))
                .isInstanceOf(ValidationException.class);
        // 平台上下文可引用全部。
        tagValidation.resolveActiveTags(List.of(orgTag.tagId()), TagScopeContext.platform());
    }
}
