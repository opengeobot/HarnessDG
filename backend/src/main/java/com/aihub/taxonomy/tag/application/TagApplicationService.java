/*
 * 功能: 受控标签应用服务，编排标签的查询、创建、更新与启用/停用，拒绝自由标签。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.taxonomy.tag.application;

import com.aihub.shared.error.ConflictException;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.error.NotFoundException;
import com.aihub.shared.error.ValidationException;
import com.aihub.shared.id.IdGenerator;
import com.aihub.shared.id.IdPrefix;
import com.aihub.taxonomy.application.AuditPort;
import com.aihub.taxonomy.domain.TaxonomyStatus;
import com.aihub.taxonomy.tag.application.TagDtos.CreateTagCommand;
import com.aihub.taxonomy.tag.application.TagDtos.TagScopeContext;
import com.aihub.taxonomy.tag.application.TagDtos.TagView;
import com.aihub.taxonomy.tag.application.TagDtos.UpdateTagCommand;
import com.aihub.taxonomy.tag.domain.Tag;
import com.aihub.taxonomy.tag.domain.TagRepository;
import com.aihub.taxonomy.tag.domain.TagScopeType;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 受控标签应用服务。
 *
 * <p>标签是治理资源：资产写接口只接受已登记的 {@code tagId}，<b>拒绝自由标签</b>。
 * (scopeType, scopeId, tagCode) 唯一，重复创建冲突。停用标签保留 asset_tag 历史关联与回显，
 * 但 {@link TagValidationService#resolveActiveTags} 拒绝其用于新建关联。
 *
 * <p><b>作用域校验（P0-B 简化）：</b>创建标签时，PLATFORM 标签 scopeId 固定为 {@code PLATFORM}；
 * ORGANIZATION 标签需调用者提供所属 organizationId 作为 scopeId（适配层据 tag:manage 权限与组织成员关系校验，
 * 本应用层不直连 organization 模块以避免循环依赖，仅校验 scopeId 非空且非 PLATFORM）。
 */
@Service
public class TagApplicationService {

    /** 标签编码格式：小写字母/数字开头，小写字母/数字/点/下划线/连字符，1~64 位（与契约 pattern 对齐）。 */
    static final Pattern TAG_CODE_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9._-]{0,63}$");

    private static final String PLATFORM_SCOPE_ID = "PLATFORM";

    private final TagRepository repository;
    private final IdGenerator idGenerator;
    private final AuditPort auditPort;

    public TagApplicationService(TagRepository repository, IdGenerator idGenerator, AuditPort auditPort) {
        this.repository = repository;
        this.idGenerator = idGenerator;
        this.auditPort = auditPort;
    }

    /**
     * 列出标签（可按作用域/状态/关键字过滤）。
     */
    @Transactional(readOnly = true)
    public List<TagView> listTags(TagScopeType scopeType, String scopeId,
                                  TaxonomyStatus status, String keyword) {
        return repository.search(scopeType, scopeId, status, keyword).stream()
                .map(TagView::from).toList();
    }

    /**
     * 创建受控标签。
     */
    @Transactional
    public TagView createTag(CreateTagCommand command, String actorId) {
        if (command == null) {
            throw new ValidationException("command is required");
        }
        TagScopeType scopeType = command.scopeType();
        if (scopeType == null) {
            throw new ValidationException("scopeType is required");
        }
        String scopeId = resolveScopeId(scopeType, command.scopeId());
        validateTagCode(command.tagCode());
        if (command.displayName() == null || command.displayName().isBlank()
                || command.displayName().length() > 128) {
            throw new ValidationException("displayName is required and must be at most 128 characters");
        }
        if (command.i18nKey() == null || command.i18nKey().isBlank()) {
            throw new ValidationException("i18nKey is required");
        }
        if (repository.existsByCode(scopeType, scopeId, command.tagCode())) {
            throw new ConflictException(ErrorCode.TAG_ALREADY_EXISTS,
                    "tag already exists in scope",
                    Map.of("scopeType", scopeType.name(), "scopeId", scopeId, "tagCode", command.tagCode()));
        }
        Tag tag = new Tag(
                idGenerator.generate(IdPrefix.TAG),
                scopeType,
                scopeId,
                command.tagCode(),
                command.displayName().trim(),
                command.i18nKey().trim(),
                command.color(),
                TaxonomyStatus.ACTIVE,
                actorId,
                1L);
        repository.insert(tag);
        auditPort.record("TAG_CREATED", actorId, tag.tagId(),
                Map.of("scopeType", scopeType.name(), "scopeId", scopeId, "tagCode", tag.tagCode()));
        return TagView.from(tag);
    }

    /**
     * 更新标签（displayName/i18nKey/color）。
     */
    @Transactional
    public TagView updateTag(String tagId, UpdateTagCommand command, String actorId) {
        if (command == null) {
            throw new ValidationException("command is required");
        }
        Tag existing = repository.findByTagId(tagId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.TAG_NOT_FOUND,
                        "tag not found", Map.of("tagId", tagId)));
        Long expectedVersion = command.expectedVersion();
        if (expectedVersion != null && expectedVersion != existing.version()) {
            throw new ConflictException(ErrorCode.CONCURRENT_MODIFICATION,
                    "tag version mismatch",
                    Map.of("expected", expectedVersion, "actual", existing.version()));
        }
        String newName = command.displayName() == null ? existing.name() : command.displayName().trim();
        if (newName.isBlank() || newName.length() > 128) {
            throw new ValidationException("displayName must be 1..128 characters");
        }
        String newI18nKey = command.i18nKey() == null ? existing.i18nKey() : command.i18nKey().trim();
        if (newI18nKey.isBlank()) {
            throw new ValidationException("i18nKey must not be blank");
        }
        String newColor = command.color() == null ? existing.color() : command.color();
        Tag updated = new Tag(existing.tagId(), existing.scopeType(), existing.scopeId(),
                existing.tagCode(), newName, newI18nKey, newColor, existing.status(),
                existing.createdBy(), existing.version() + 1L);
        repository.update(updated, existing.version());
        auditPort.record("TAG_UPDATED", actorId, existing.tagId(),
                Map.of("tagCode", existing.tagCode(), "version", updated.version()));
        return TagView.from(updated);
    }

    /**
     * 启用标签。
     */
    @Transactional
    public void enableTag(String tagId, Long expectedVersion, String actorId) {
        setStatus(tagId, TaxonomyStatus.ACTIVE, expectedVersion, actorId, "TAG_ENABLED");
    }

    /**
     * 停用标签（保留 asset_tag 历史关联与回显，仅禁止新建关联）。
     */
    @Transactional
    public void disableTag(String tagId, Long expectedVersion, String actorId) {
        setStatus(tagId, TaxonomyStatus.DISABLED, expectedVersion, actorId, "TAG_DISABLED");
    }

    private void setStatus(String tagId, TaxonomyStatus status, Long expectedVersion,
                           String actorId, String eventType) {
        Tag existing = repository.findByTagId(tagId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.TAG_NOT_FOUND,
                        "tag not found", Map.of("tagId", tagId)));
        if (expectedVersion != null && expectedVersion != existing.version()) {
            throw new ConflictException(ErrorCode.CONCURRENT_MODIFICATION,
                    "tag version mismatch",
                    Map.of("expected", expectedVersion, "actual", existing.version()));
        }
        repository.updateStatus(tagId, status, existing.version());
        auditPort.record(eventType, actorId, tagId, Map.of("status", status.name()));
    }

    /** 解析作用域 ID：平台标签固定 PLATFORM；组织标签必须为非空且非 PLATFORM 的组织 ID。 */
    static String resolveScopeId(TagScopeType scopeType, String scopeId) {
        if (scopeType == TagScopeType.PLATFORM) {
            if (scopeId != null && !scopeId.isBlank() && !scopeId.equals(PLATFORM_SCOPE_ID)) {
                throw new ValidationException("PLATFORM tag scopeId must be empty or 'PLATFORM'");
            }
            return PLATFORM_SCOPE_ID;
        }
        if (scopeId == null || scopeId.isBlank() || scopeId.equals(PLATFORM_SCOPE_ID)) {
            throw new ValidationException("ORGANIZATION tag scopeId must be a real organization id");
        }
        return scopeId;
    }

    static void validateTagCode(String tagCode) {
        if (tagCode == null || !TAG_CODE_PATTERN.matcher(tagCode).matches()) {
            throw new ValidationException("tagCode must match " + TAG_CODE_PATTERN.pattern());
        }
    }

    /** 暴露作用域上下文工厂，供资产模块 Task 14 构建。 */
    public TagScopeContext scopeContext(TagScopeType scopeType, String scopeId) {
        return new TagScopeContext(scopeType, resolveScopeId(scopeType, scopeId));
    }
}
