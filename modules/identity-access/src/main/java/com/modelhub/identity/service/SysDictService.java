package com.modelhub.identity.service;

import com.modelhub.identity.domain.SysDictEntity;
import com.modelhub.identity.domain.SysDictItemEntity;
import com.modelhub.identity.repo.SysDictItemRepository;
import com.modelhub.identity.repo.SysDictRepository;
import com.modelhub.identity.security.CurrentPrincipal;
import com.modelhub.shared.error.ApiException;
import com.modelhub.shared.error.ErrorCode;
import com.modelhub.shared.id.PublicIds;
import com.modelhub.shared.web.ETags;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 通用字典管理服务（管理后台计划 §四）：字典与字典项 CRUD。
 * 读仅 platform_admin；项的 item_value 不可改；删除字典要求无项。
 */
@Service
public class SysDictService {

    private static final Pattern DICT_CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9_]{1,63}$");

    /** 契约 Dict schema。 */
    public record DictView(UUID id, String dictCode, String name, String description,
                           String status, long itemCount, long version) {}

    /** 契约 DictItem schema。 */
    public record DictItemView(long id, String itemValue, String labelZh, String labelEn,
                               int sortOrder, String status, String remark) {}

    private final SysDictRepository dicts;
    private final SysDictItemRepository items;
    private final AuditService auditService;

    public SysDictService(SysDictRepository dicts, SysDictItemRepository items, AuditService auditService) {
        this.dicts = dicts;
        this.items = items;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<DictView> listDicts(CurrentPrincipal actor) {
        requireAdmin(actor);
        return dicts.findAllByOrderById().stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public DictView getDict(CurrentPrincipal actor, UUID dictPublicId) {
        requireAdmin(actor);
        return toView(requireDict(dictPublicId));
    }

    @Transactional
    public DictView createDict(CurrentPrincipal actor, String dictCode, String name, String description) {
        requireAdmin(actor);
        if (dictCode == null || !DICT_CODE_PATTERN.matcher(dictCode).matches()) {
            throw ApiException.badRequest("字典 code 必须为 2-64 位小写字母/数字/下划线，且以字母开头",
                    List.of(new ApiException.Detail("dictCode", "invalid_format")));
        }
        if (name == null || name.isBlank()) {
            throw ApiException.badRequest("字典名称不能为空",
                    List.of(new ApiException.Detail("name", "required")));
        }
        if (dicts.existsByDictCode(dictCode)) {
            throw new ApiException(ErrorCode.CONFLICT, "字典 code 已存在");
        }
        SysDictEntity dict = new SysDictEntity();
        dict.setPublicId(PublicIds.next());
        dict.setDictCode(dictCode);
        dict.setName(name.trim());
        dict.setDescription(description);
        dicts.save(dict);
        auditService.appendSimple(actor.username(), "dict.create", "dict:" + dictCode, "success");
        return toView(dict);
    }

    /** 更新字典：改名/描述，If-Match 条件更新。 */
    @Transactional
    public DictView updateDict(CurrentPrincipal actor, UUID dictPublicId, String name,
                               String description, String ifMatch) {
        requireAdmin(actor);
        SysDictEntity dict = requireDict(dictPublicId);
        ETags.requireMatch(ifMatch, ETags.ofVersion(dict.getVersion()), "字典");
        if (name != null && !name.isBlank()) {
            dict.setName(name.trim());
        }
        if (description != null) {
            dict.setDescription(description);
        }
        dict.setVersion(dict.getVersion() + 1);
        dict.setUpdatedAt(OffsetDateTime.now());
        dicts.save(dict);
        auditService.appendSimple(actor.username(), "dict.update", "dict:" + dict.getDictCode(), "success");
        return toView(dict);
    }

    @Transactional
    public void deleteDict(CurrentPrincipal actor, UUID dictPublicId) {
        requireAdmin(actor);
        SysDictEntity dict = requireDict(dictPublicId);
        if (items.countByDictId(dict.getId()) > 0) {
            throw new ApiException(ErrorCode.CONFLICT, "字典仍有字典项，不可删除");
        }
        dicts.delete(dict);
        auditService.appendSimple(actor.username(), "dict.delete", "dict:" + dict.getDictCode(), "success");
    }

    // ---------- 字典项 ----------

    @Transactional(readOnly = true)
    public List<DictItemView> listItems(CurrentPrincipal actor, UUID dictPublicId) {
        requireAdmin(actor);
        SysDictEntity dict = requireDict(dictPublicId);
        return items.findByDictIdOrderBySortOrder(dict.getId()).stream().map(this::toItemView).toList();
    }

    @Transactional
    public DictItemView createItem(CurrentPrincipal actor, UUID dictPublicId, String itemValue,
                                   String labelZh, String labelEn, Integer sortOrder, String remark) {
        requireAdmin(actor);
        SysDictEntity dict = requireDict(dictPublicId);
        if (itemValue == null || itemValue.isBlank()) {
            throw ApiException.badRequest("字典项值不能为空",
                    List.of(new ApiException.Detail("itemValue", "required")));
        }
        if (labelZh == null || labelZh.isBlank() || labelEn == null || labelEn.isBlank()) {
            throw ApiException.badRequest("字典项中英文标签均不能为空",
                    List.of(new ApiException.Detail("label", "required")));
        }
        if (items.findByDictIdAndItemValue(dict.getId(), itemValue.trim()).isPresent()) {
            throw new ApiException(ErrorCode.CONFLICT, "字典项值已存在");
        }
        SysDictItemEntity item = new SysDictItemEntity();
        item.setDictId(dict.getId());
        item.setItemValue(itemValue.trim());
        item.setLabelZh(labelZh.trim());
        item.setLabelEn(labelEn.trim());
        item.setSortOrder(sortOrder == null ? 0 : sortOrder);
        item.setRemark(remark);
        items.save(item);
        auditService.appendSimple(actor.username(), "dict.item_create",
                "dict:" + dict.getDictCode() + ":item:" + item.getItemValue(), "success");
        return toItemView(item);
    }

    /** 更新字典项：标签/排序/备注可改，item_value 不可改（稳定键）。 */
    @Transactional
    public DictItemView updateItem(CurrentPrincipal actor, UUID dictPublicId, long itemId,
                                   String labelZh, String labelEn, Integer sortOrder, String remark) {
        requireAdmin(actor);
        SysDictEntity dict = requireDict(dictPublicId);
        SysDictItemEntity item = requireItem(dict, itemId);
        if (labelZh != null && !labelZh.isBlank()) {
            item.setLabelZh(labelZh.trim());
        }
        if (labelEn != null && !labelEn.isBlank()) {
            item.setLabelEn(labelEn.trim());
        }
        if (sortOrder != null) {
            item.setSortOrder(sortOrder);
        }
        if (remark != null) {
            item.setRemark(remark);
        }
        items.save(item);
        auditService.appendSimple(actor.username(), "dict.item_update",
                "dict:" + dict.getDictCode() + ":item:" + item.getItemValue(), "success");
        return toItemView(item);
    }

    @Transactional
    public DictItemView setItemStatus(CurrentPrincipal actor, UUID dictPublicId, long itemId, boolean active) {
        requireAdmin(actor);
        SysDictEntity dict = requireDict(dictPublicId);
        SysDictItemEntity item = requireItem(dict, itemId);
        String target = active ? "active" : "disabled";
        if (target.equals(item.getStatus())) {
            throw new ApiException(ErrorCode.CONFLICT, active ? "字典项已启用" : "字典项已停用");
        }
        item.setStatus(target);
        items.save(item);
        auditService.appendSimple(actor.username(), active ? "dict.item_enable" : "dict.item_disable",
                "dict:" + dict.getDictCode() + ":item:" + item.getItemValue(), "success");
        return toItemView(item);
    }

    @Transactional
    public void deleteItem(CurrentPrincipal actor, UUID dictPublicId, long itemId) {
        requireAdmin(actor);
        SysDictEntity dict = requireDict(dictPublicId);
        SysDictItemEntity item = requireItem(dict, itemId);
        // v1 通用字典暂无业务引用点；后续接入业务表后在此补充引用检查
        items.delete(item);
        auditService.appendSimple(actor.username(), "dict.item_delete",
                "dict:" + dict.getDictCode() + ":item:" + item.getItemValue(), "success");
    }

    // ---------- 内部 ----------

    private DictView toView(SysDictEntity dict) {
        return new DictView(dict.getPublicId(), dict.getDictCode(), dict.getName(), dict.getDescription(),
                dict.getStatus(), items.countByDictId(dict.getId()), dict.getVersion());
    }

    private DictItemView toItemView(SysDictItemEntity item) {
        return new DictItemView(item.getId(), item.getItemValue(), item.getLabelZh(), item.getLabelEn(),
                item.getSortOrder(), item.getStatus(), item.getRemark());
    }

    private SysDictEntity requireDict(UUID dictPublicId) {
        return dicts.findByPublicId(dictPublicId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "字典不存在"));
    }

    private SysDictItemEntity requireItem(SysDictEntity dict, long itemId) {
        SysDictItemEntity item = items.findById(itemId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "字典项不存在"));
        if (!item.getDictId().equals(dict.getId())) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "字典项不存在");
        }
        return item;
    }

    private static void requireAdmin(CurrentPrincipal actor) {
        if (actor == null) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "未认证或凭证已过期");
        }
        if (!actor.isPlatformAdmin()) {
            throw new ApiException(ErrorCode.FORBIDDEN, "仅 platform_admin 可执行该操作");
        }
    }
}
