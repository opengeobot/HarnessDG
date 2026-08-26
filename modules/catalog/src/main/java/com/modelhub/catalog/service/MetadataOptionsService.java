package com.modelhub.catalog.service;

import com.modelhub.identity.domain.SysDictEntity;
import com.modelhub.identity.domain.SysDictItemEntity;
import com.modelhub.identity.repo.SysDictItemRepository;
import com.modelhub.identity.repo.SysDictRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * metadata 选项服务（契约 MetadataOptions/TaxonomyOption）：匿名可读。
 * 字典统一（V18）后改读 sys_dict/sys_dict_item 单一字典体系：仅返回市场类
 * 字典（9 个白名单），排除 sys_user_status 等管理字典；disabled 项以
 * 'deprecated' 状态保留返回供旧数据回显。
 */
@Service
public class MetadataOptionsService {

    /** 契约 TaxonomyOption schema。 */
    public record TaxonomyOption(String key, String displayName, String displayNameEn,
                                 String parentKey, String status) {}

    /** 契约 MetadataOptions schema。 */
    public record MetadataOptionsView(String version, Map<String, List<TaxonomyOption>> taxonomies) {}

    /** 市场筛选分类字典白名单（V4/V5 taxonomy 迁入，V18）；管理字典不进入市场选项。 */
    static final Set<String> MARKET_DICT_CODES = Set.of(
            "framework", "license", "architecture", "language", "tag",
            "capability", "scene", "model_task", "dataset_task");

    private final SysDictRepository dicts;
    private final SysDictItemRepository dictItems;

    public MetadataOptionsService(SysDictRepository dicts, SysDictItemRepository dictItems) {
        this.dicts = dicts;
        this.dictItems = dictItems;
    }

    @Transactional(readOnly = true)
    public MetadataOptionsView options() {
        Map<String, List<TaxonomyOption>> result = new LinkedHashMap<>();
        for (SysDictEntity dict : dicts.findAllByOrderById()) {
            if (!MARKET_DICT_CODES.contains(dict.getDictCode())) {
                continue;
            }
            List<SysDictItemEntity> items = dictItems.findByDictIdOrderBySortOrder(dict.getId());
            Map<Long, String> keyById = new HashMap<>();
            items.forEach(i -> keyById.put(i.getId(), i.getItemValue()));
            result.put(dict.getDictCode(), items.stream()
                    .map(i -> new TaxonomyOption(i.getItemValue(), i.getLabelZh(), i.getLabelEn(),
                            i.getParentId() == null ? null : keyById.get(i.getParentId()),
                            contractStatus(i.getStatus())))
                    .toList());
        }
        return new MetadataOptionsView("v1", result);
    }

    /** 字典项状态 → 契约状态（契约枚举为 active/deprecated，disabled 映射回 deprecated）。 */
    private static String contractStatus(String dictItemStatus) {
        return "disabled".equals(dictItemStatus) ? "deprecated" : dictItemStatus;
    }
}
