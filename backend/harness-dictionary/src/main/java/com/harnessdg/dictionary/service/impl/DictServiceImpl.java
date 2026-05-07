package com.harnessdg.dictionary.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.harnessdg.common.exception.BizException;
import com.harnessdg.common.i18n.I18nTextUtils;
import com.harnessdg.common.response.ErrorCode;
import com.harnessdg.dictionary.cache.DictCacheManager;
import com.harnessdg.dictionary.mapper.SysDictGroupMapper;
import com.harnessdg.dictionary.mapper.SysDictItemMapper;
import com.harnessdg.dictionary.service.DictService;
import com.harnessdg.model.dict.dto.*;
import com.harnessdg.model.dict.entity.SysDictGroup;
import com.harnessdg.model.dict.entity.SysDictItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DictServiceImpl implements DictService {

    private final SysDictGroupMapper groupMapper;
    private final SysDictItemMapper itemMapper;
    private final DictCacheManager cacheManager;

    @Override
    public List<DictGroupDTO> listGroups(String category, String status) {
        LambdaQueryWrapper<SysDictGroup> wrapper = new LambdaQueryWrapper<>();
        if (category != null && !category.isBlank()) {
            wrapper.eq(SysDictGroup::getCategory, category);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(SysDictGroup::getStatus, status);
        }
        wrapper.orderByAsc(SysDictGroup::getCode);
        return groupMapper.selectList(wrapper).stream()
                .map(this::toGroupDTO)
                .collect(Collectors.toList());
    }

    @Override
    public DictGroupDTO getGroupByCode(String code, String locale) {
        SysDictGroup group = findGroupByCode(code);
        DictGroupDTO dto = toGroupDTO(group);
        if (locale != null) {
            dto.setResolvedName(I18nTextUtils.resolve(group.getName(), locale));
            dto.setResolvedDescription(I18nTextUtils.resolve(group.getDescription(), locale));
        }
        return dto;
    }

    @Override
    @Transactional
    public DictGroupDTO createGroup(DictGroupCreateRequest request) {
        // 检查 code 唯一性
        LambdaQueryWrapper<SysDictGroup> check = new LambdaQueryWrapper<>();
        check.eq(SysDictGroup::getCode, request.getCode());
        if (groupMapper.selectCount(check) > 0) {
            throw new BizException(ErrorCode.DICT_GROUP_EXISTS);
        }

        SysDictGroup entity = new SysDictGroup();
        entity.setCode(request.getCode());
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        entity.setCategory(request.getCategory());
        entity.setIsTree(request.getIsTree());
        entity.setIsMultiple(request.getIsMultiple());
        entity.setIsEditable(request.getIsEditable());
        entity.setStatus("active");
        groupMapper.insert(entity);
        return toGroupDTO(entity);
    }

    @Override
    @Transactional
    public DictGroupDTO updateGroup(String code, DictGroupUpdateRequest request) {
        SysDictGroup group = findGroupByCode(code);

        if (request.getName() != null) group.setName(request.getName());
        if (request.getDescription() != null) group.setDescription(request.getDescription());
        if (request.getCategory() != null) group.setCategory(request.getCategory());
        if (request.getIsTree() != null) group.setIsTree(request.getIsTree());
        if (request.getIsMultiple() != null) group.setIsMultiple(request.getIsMultiple());
        if (request.getIsEditable() != null) group.setIsEditable(request.getIsEditable());
        if (request.getStatus() != null) group.setStatus(request.getStatus());

        groupMapper.updateById(group);
        cacheManager.evictGroup(code);
        return toGroupDTO(group);
    }

    @Override
    @Transactional
    public void deleteGroup(String code) {
        SysDictGroup group = findGroupByCode(code);
        groupMapper.deleteById(group.getId());

        // 同时逻辑删除该分组下所有字典项
        LambdaUpdateWrapper<SysDictItem> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(SysDictItem::getGroupCode, code);
        itemMapper.delete(wrapper);

        cacheManager.evictGroup(code);
    }

    @Override
    public List<DictItemDTO> listItems(String groupCode, String locale) {
        List<DictItemDTO> cached = cacheManager.getItems(groupCode);
        if (cached != null) {
            return resolveItemLocale(cached, locale);
        }

        LambdaQueryWrapper<SysDictItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysDictItem::getGroupCode, groupCode)
                .orderByAsc(SysDictItem::getSortOrder)
                .orderByAsc(SysDictItem::getId);
        List<DictItemDTO> items = itemMapper.selectList(wrapper).stream()
                .map(this::toItemDTO)
                .collect(Collectors.toList());

        cacheManager.putItems(groupCode, items);
        return resolveItemLocale(items, locale);
    }

    @Override
    public List<DictItemDTO> getItemTree(String groupCode, String locale) {
        List<DictItemDTO> flatList = listItems(groupCode, locale);
        return buildTree(flatList);
    }

    @Override
    @Transactional
    public DictItemDTO createItem(DictItemCreateRequest request) {
        SysDictItem entity = new SysDictItem();
        entity.setGroupCode(request.getGroupCode());
        entity.setParentId(request.getParentId());
        entity.setCode(request.getCode());
        entity.setLabel(request.getLabel());
        entity.setDescription(request.getDescription());
        entity.setValue(request.getValue());
        entity.setIcon(request.getIcon());
        entity.setColor(request.getColor());
        entity.setSortOrder(request.getSortOrder());
        entity.setIsDefault(request.getIsDefault());
        entity.setIsSystem(false);
        entity.setStatus("active");
        entity.setExtra(request.getExtra());
        itemMapper.insert(entity);

        cacheManager.evictGroup(request.getGroupCode());
        return toItemDTO(entity);
    }

    @Override
    @Transactional
    public DictItemDTO updateItem(Long id, DictItemUpdateRequest request) {
        SysDictItem item = itemMapper.selectById(id);
        if (item == null) {
            throw new BizException(ErrorCode.DICT_ITEM_NOT_FOUND);
        }

        if (request.getParentId() != null) item.setParentId(request.getParentId());
        if (request.getLabel() != null) item.setLabel(request.getLabel());
        if (request.getDescription() != null) item.setDescription(request.getDescription());
        if (request.getValue() != null) item.setValue(request.getValue());
        if (request.getIcon() != null) item.setIcon(request.getIcon());
        if (request.getColor() != null) item.setColor(request.getColor());
        if (request.getSortOrder() != null) item.setSortOrder(request.getSortOrder());
        if (request.getIsDefault() != null) item.setIsDefault(request.getIsDefault());
        if (request.getStatus() != null) item.setStatus(request.getStatus());
        if (request.getExtra() != null) item.setExtra(request.getExtra());

        itemMapper.updateById(item);
        cacheManager.evictGroup(item.getGroupCode());
        return toItemDTO(item);
    }

    @Override
    @Transactional
    public void deleteItem(Long id) {
        SysDictItem item = itemMapper.selectById(id);
        if (item == null) {
            throw new BizException(ErrorCode.DICT_ITEM_NOT_FOUND);
        }
        if (Boolean.TRUE.equals(item.getIsSystem())) {
            throw new BizException(ErrorCode.DICT_SYSTEM_ITEM_CANNOT_DELETE);
        }
        itemMapper.deleteById(id);
        cacheManager.evictGroup(item.getGroupCode());
    }

    @Override
    @Transactional
    public List<DictItemDTO> batchCreateItems(String groupCode, List<DictItemCreateRequest> requests) {
        List<DictItemDTO> results = new ArrayList<>();
        for (DictItemCreateRequest request : requests) {
            request.setGroupCode(groupCode);
            results.add(createItem(request));
        }
        return results;
    }

    @Override
    public Map<String, List<DictItemDTO>> batchGetItems(List<String> groupCodes, String locale) {
        Map<String, List<DictItemDTO>> result = new LinkedHashMap<>();
        if (groupCodes == null || groupCodes.isEmpty()) {
            return result;
        }
        for (String groupCode : groupCodes) {
            if (groupCode == null || groupCode.isBlank()) continue;
            result.put(groupCode, listItems(groupCode, locale));
        }
        return result;
    }

    @Override
    public List<DictItemDTO> searchItems(String keyword, String groupCode, String locale) {
        LambdaQueryWrapper<SysDictItem> wrapper = new LambdaQueryWrapper<>();
        if (groupCode != null && !groupCode.isBlank()) {
            wrapper.eq(SysDictItem::getGroupCode, groupCode);
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(SysDictItem::getCode, kw)
                    .or().like(SysDictItem::getValue, kw)
                    .or().apply("CAST(label AS TEXT) ILIKE {0}", "%" + kw + "%")
                    .or().apply("CAST(description AS TEXT) ILIKE {0}", "%" + kw + "%"));
        }
        wrapper.orderByAsc(SysDictItem::getGroupCode)
                .orderByAsc(SysDictItem::getSortOrder)
                .orderByAsc(SysDictItem::getId)
                .last("LIMIT 200");
        List<DictItemDTO> items = itemMapper.selectList(wrapper).stream()
                .map(this::toItemDTO)
                .collect(Collectors.toList());
        return resolveItemLocale(items, locale);
    }

    @Override
    public DictExportData exportGroup(String groupCode) {
        SysDictGroup group = findGroupByCode(groupCode);
        DictExportData data = new DictExportData();

        DictGroupCreateRequest groupReq = new DictGroupCreateRequest();
        groupReq.setCode(group.getCode());
        groupReq.setName(group.getName());
        groupReq.setDescription(group.getDescription());
        groupReq.setCategory(group.getCategory());
        groupReq.setIsTree(group.getIsTree());
        groupReq.setIsMultiple(group.getIsMultiple());
        groupReq.setIsEditable(group.getIsEditable());
        data.setGroup(groupReq);

        LambdaQueryWrapper<SysDictItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysDictItem::getGroupCode, groupCode)
                .orderByAsc(SysDictItem::getSortOrder)
                .orderByAsc(SysDictItem::getId);
        List<DictItemCreateRequest> items = itemMapper.selectList(wrapper).stream()
                .map(entity -> {
                    DictItemCreateRequest req = new DictItemCreateRequest();
                    req.setGroupCode(entity.getGroupCode());
                    req.setParentId(entity.getParentId());
                    req.setCode(entity.getCode());
                    req.setLabel(entity.getLabel());
                    req.setDescription(entity.getDescription());
                    req.setValue(entity.getValue());
                    req.setIcon(entity.getIcon());
                    req.setColor(entity.getColor());
                    req.setSortOrder(entity.getSortOrder());
                    req.setIsDefault(entity.getIsDefault());
                    req.setExtra(entity.getExtra());
                    return req;
                })
                .collect(Collectors.toList());
        data.setItems(items);
        data.setExportedAt(System.currentTimeMillis());
        return data;
    }

    @Override
    @Transactional
    public DictGroupDTO importGroup(DictExportData data, boolean overwrite) {
        if (data == null || data.getGroup() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST);
        }
        DictGroupCreateRequest groupReq = data.getGroup();
        String code = groupReq.getCode();

        LambdaQueryWrapper<SysDictGroup> check = new LambdaQueryWrapper<>();
        check.eq(SysDictGroup::getCode, code);
        SysDictGroup existing = groupMapper.selectOne(check);

        DictGroupDTO groupDTO;
        if (existing != null) {
            if (!overwrite) {
                throw new BizException(ErrorCode.DICT_GROUP_EXISTS);
            }
            // 覆盖模式：清空已有字典项，更新分组元信息
            LambdaUpdateWrapper<SysDictItem> del = new LambdaUpdateWrapper<>();
            del.eq(SysDictItem::getGroupCode, code);
            itemMapper.delete(del);

            existing.setName(groupReq.getName());
            existing.setDescription(groupReq.getDescription());
            existing.setCategory(groupReq.getCategory());
            existing.setIsTree(groupReq.getIsTree());
            existing.setIsMultiple(groupReq.getIsMultiple());
            existing.setIsEditable(groupReq.getIsEditable());
            groupMapper.updateById(existing);
            groupDTO = toGroupDTO(existing);
        } else {
            groupDTO = createGroup(groupReq);
        }

        if (data.getItems() != null && !data.getItems().isEmpty()) {
            // 导入时 parentId 可能引用导出时的旧 id，此版本 DictItemCreateRequest 不携带原始 id，
            // 暂先按 parentId=null 先的顺序插入，保留层级输入依赖
            List<DictItemCreateRequest> sorted = new ArrayList<>(data.getItems());
            sorted.sort((a, b) -> {
                Long pa = a.getParentId();
                Long pb = b.getParentId();
                if (pa == null && pb == null) return 0;
                if (pa == null) return -1;
                if (pb == null) return 1;
                return pa.compareTo(pb);
            });

            for (DictItemCreateRequest req : sorted) {
                req.setGroupCode(code);
                // 简化策略：旧的 parentId 无法映射到新 id，统一置空降为根节点
                // 若未来需要严格维护树结构，可为 DictItemCreateRequest 扩展 originalId/originalParentId 字段
                req.setParentId(null);
                createItem(req);
            }
        }
        cacheManager.evictGroup(code);
        return groupDTO;
    }

    // === Private helpers ===

    private SysDictGroup findGroupByCode(String code) {
        LambdaQueryWrapper<SysDictGroup> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysDictGroup::getCode, code);
        SysDictGroup group = groupMapper.selectOne(wrapper);
        if (group == null) {
            throw new BizException(ErrorCode.DICT_GROUP_NOT_FOUND);
        }
        return group;
    }

    private DictGroupDTO toGroupDTO(SysDictGroup entity) {
        DictGroupDTO dto = new DictGroupDTO();
        dto.setId(entity.getId());
        dto.setCode(entity.getCode());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setCategory(entity.getCategory());
        dto.setIsTree(entity.getIsTree());
        dto.setIsMultiple(entity.getIsMultiple());
        dto.setIsEditable(entity.getIsEditable());
        dto.setStatus(entity.getStatus());
        return dto;
    }

    private DictItemDTO toItemDTO(SysDictItem entity) {
        DictItemDTO dto = new DictItemDTO();
        dto.setId(entity.getId());
        dto.setGroupCode(entity.getGroupCode());
        dto.setParentId(entity.getParentId());
        dto.setCode(entity.getCode());
        dto.setLabel(entity.getLabel());
        dto.setDescription(entity.getDescription());
        dto.setValue(entity.getValue());
        dto.setIcon(entity.getIcon());
        dto.setColor(entity.getColor());
        dto.setSortOrder(entity.getSortOrder());
        dto.setIsDefault(entity.getIsDefault());
        dto.setIsSystem(entity.getIsSystem());
        dto.setStatus(entity.getStatus());
        dto.setExtra(entity.getExtra());
        return dto;
    }

    private List<DictItemDTO> resolveItemLocale(List<DictItemDTO> items, String locale) {
        if (locale == null) return items;
        return items.stream().peek(item -> {
            item.setResolvedLabel(I18nTextUtils.resolve(item.getLabel(), locale));
            item.setResolvedDescription(I18nTextUtils.resolve(item.getDescription(), locale));
        }).collect(Collectors.toList());
    }

    private List<DictItemDTO> buildTree(List<DictItemDTO> flatList) {
        Map<Long, DictItemDTO> map = new LinkedHashMap<>();
        for (DictItemDTO item : flatList) {
            item.setChildren(new ArrayList<>());
            map.put(item.getId(), item);
        }

        List<DictItemDTO> roots = new ArrayList<>();
        for (DictItemDTO item : flatList) {
            if (item.getParentId() == null) {
                roots.add(item);
            } else {
                DictItemDTO parent = map.get(item.getParentId());
                if (parent != null) {
                    parent.getChildren().add(item);
                } else {
                    roots.add(item);
                }
            }
        }
        return roots;
    }
}
