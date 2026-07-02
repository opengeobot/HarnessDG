/*
 * 功能: 字典应用服务，编排字典类型/项的查询、新建、更新与停用，并暴露治理字段校验端口。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.taxonomy.dictionary.application;

import com.aihub.shared.error.ConflictException;
import com.aihub.shared.error.ErrorCode;
import com.aihub.shared.error.NotFoundException;
import com.aihub.shared.error.ValidationException;
import com.aihub.shared.id.IdGenerator;
import com.aihub.taxonomy.application.AuditPort;
import com.aihub.taxonomy.dictionary.application.DictionaryDtos.CreateDictionaryItemCommand;
import com.aihub.taxonomy.dictionary.application.DictionaryDtos.DictionaryItemView;
import com.aihub.taxonomy.dictionary.application.DictionaryDtos.DictionaryTypeView;
import com.aihub.taxonomy.dictionary.application.DictionaryDtos.UpdateDictionaryItemCommand;
import com.aihub.taxonomy.dictionary.domain.DictionaryItem;
import com.aihub.taxonomy.dictionary.domain.DictionaryRepository;
import com.aihub.taxonomy.dictionary.domain.DictionaryType;
import com.aihub.taxonomy.domain.TaxonomyStatus;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 字典应用服务。
 *
 * <p>字典只存稳定的 {@code itemCode} 与 {@code i18nKey}，绝不存展示文案。字典项新增/修改/停用时
 * 自增所属字典类型的缓存版本，并经 {@link AuditPort} 记录（TODO Task 10 权威实现）。
 * 同时实现 {@link DictionaryValidationPort}，供资产模块 Task 14 校验治理字段（license/framework 等）。
 *
 * <p><b>停用语义：</b>停用项保留并可回显（{@link #isKnown} 返回 true），但不可用于新建引用
 * （{@link #validateItemCode} 拒绝）。
 */
@Service
public class DictionaryApplicationService implements DictionaryValidationPort {

    /** 字典项编码格式：字母/数字开头，字母/数字/点/下划线/连字符，1~64 位（与契约 pattern 对齐）。 */
    static final Pattern ITEM_CODE_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}$");

    private static final String DICT_ID_PREFIX = "dct";

    private final DictionaryRepository repository;
    private final IdGenerator idGenerator;
    private final AuditPort auditPort;
    private final Clock clock;

    public DictionaryApplicationService(DictionaryRepository repository,
                                        IdGenerator idGenerator,
                                        AuditPort auditPort,
                                        Clock clock) {
        this.repository = repository;
        this.idGenerator = idGenerator;
        this.auditPort = auditPort;
        this.clock = clock;
    }

    /**
     * 列出全部字典类型。
     */
    @Transactional(readOnly = true)
    public List<DictionaryTypeView> listTypes() {
        return repository.findAllTypes().stream().map(DictionaryTypeView::from).toList();
    }

    /**
     * 列出字典项。
     *
     * @param dictCode        字典编码
     * @param includeDisabled 是否含停用项（true 用于历史回显，false 仅可引用项）
     */
    @Transactional(readOnly = true)
    public List<DictionaryItemView> listItems(String dictCode, boolean includeDisabled) {
        requireTypeExists(dictCode);
        return repository.findItems(dictCode, includeDisabled).stream()
                .map(DictionaryItemView::from).toList();
    }

    /**
     * 新建字典项。
     */
    @Transactional
    public DictionaryItemView createItem(String dictCode, CreateDictionaryItemCommand command, String actorId) {
        requireTypeExists(dictCode);
        if (command == null) {
            throw new ValidationException("command is required");
        }
        validateItemCodeFormat(command.itemCode());
        if (command.i18nKey() == null || command.i18nKey().isBlank()) {
            throw new ValidationException("i18nKey is required");
        }
        if (repository.findItem(dictCode, command.itemCode()).isPresent()) {
            throw new ConflictException(ErrorCode.DICTIONARY_ITEM_ALREADY_EXISTS,
                    "dictionary item already exists",
                    Map.of("dictCode", dictCode, "itemCode", command.itemCode()));
        }
        int sortOrder = command.sortOrder() == null ? 0 : command.sortOrder();
        DictionaryItem item = new DictionaryItem(
                idGenerator.generate(DICT_ID_PREFIX),
                dictCode,
                command.itemCode(),
                command.i18nKey().trim(),
                sortOrder,
                TaxonomyStatus.ACTIVE,
                1L);
        repository.insertItem(item);
        repository.incrementTypeVersion(dictCode);
        auditPort.record("DICTIONARY_ITEM_CREATED", actorId, item.dictItemId(),
                Map.of("dictCode", dictCode, "itemCode", item.itemCode(), "i18nKey", item.i18nKey()));
        return DictionaryItemView.from(item);
    }

    /**
     * 更新字典项（修改 i18nKey/sortOrder 或停用/启用）。
     */
    @Transactional
    public DictionaryItemView updateItem(String dictCode, String itemCode,
                                         UpdateDictionaryItemCommand command, String actorId) {
        requireTypeExists(dictCode);
        if (command == null) {
            throw new ValidationException("command is required");
        }
        DictionaryItem existing = repository.findItem(dictCode, itemCode)
                .orElseThrow(() -> new NotFoundException(ErrorCode.DICTIONARY_ITEM_NOT_FOUND,
                        "dictionary item not found",
                        Map.of("dictCode", dictCode, "itemCode", itemCode)));
        Long expectedVersion = command.expectedVersion();
        if (expectedVersion != null && expectedVersion != existing.version()) {
            throw new ConflictException(ErrorCode.CONCURRENT_MODIFICATION,
                    "dictionary item version mismatch",
                    Map.of("expected", expectedVersion, "actual", existing.version()));
        }
        String newI18nKey = command.i18nKey() == null ? existing.i18nKey() : command.i18nKey().trim();
        if (newI18nKey.isBlank()) {
            throw new ValidationException("i18nKey must not be blank");
        }
        int newSortOrder = command.sortOrder() == null ? existing.sortOrder() : command.sortOrder();
        TaxonomyStatus newStatus = command.status() == null ? existing.status() : command.status();
        DictionaryItem updated = new DictionaryItem(
                existing.dictItemId(), dictCode, itemCode, newI18nKey, newSortOrder, newStatus,
                existing.version() + 1L);
        repository.updateItem(updated, existing.version());
        repository.incrementTypeVersion(dictCode);
        auditPort.record("DICTIONARY_ITEM_UPDATED", actorId, existing.dictItemId(),
                Map.of("dictCode", dictCode, "itemCode", itemCode,
                        "status", newStatus.name(), "version", updated.version()));
        return DictionaryItemView.from(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isKnown(String dictCode, String itemCode) {
        return repository.findItem(dictCode, itemCode).isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public void validateItemCode(String dictCode, String itemCode) {
        DictionaryItem item = repository.findItem(dictCode, itemCode).orElse(null);
        if (item == null || item.status() != TaxonomyStatus.ACTIVE) {
            throw new ValidationException(ErrorCode.DICTIONARY_VALUE_INVALID,
                    "dictionary item is unknown or disabled",
                    Map.of("dictCode", dictCode, "itemCode", String.valueOf(itemCode)));
        }
    }

    private void requireTypeExists(String dictCode) {
        if (!repository.typeExists(dictCode)) {
            throw new NotFoundException(ErrorCode.DICTIONARY_ITEM_NOT_FOUND,
                    "dictionary type not found", Map.of("dictCode", dictCode));
        }
    }

    static void validateItemCodeFormat(String itemCode) {
        if (itemCode == null || !ITEM_CODE_PATTERN.matcher(itemCode).matches()) {
            throw new ValidationException("itemCode must match " + ITEM_CODE_PATTERN.pattern());
        }
    }
}
