package com.modelhub.catalog.service;

import com.modelhub.catalog.domain.TaxonomyEntity;
import com.modelhub.catalog.domain.TaxonomyValueEntity;
import com.modelhub.catalog.repo.TaxonomyRepository;
import com.modelhub.catalog.repo.TaxonomyValueRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * metadata 选项服务（契约 MetadataOptions/TaxonomyOption）：匿名可读，
 * deprecated 值保留返回供旧数据回显。
 */
@Service
public class MetadataOptionsService {

    /** 契约 TaxonomyOption schema。 */
    public record TaxonomyOption(String key, String displayName, String parentKey, String status) {}

    /** 契约 MetadataOptions schema。 */
    public record MetadataOptionsView(String version, Map<String, List<TaxonomyOption>> taxonomies) {}

    private final TaxonomyRepository taxonomies;
    private final TaxonomyValueRepository taxonomyValues;

    public MetadataOptionsService(TaxonomyRepository taxonomies, TaxonomyValueRepository taxonomyValues) {
        this.taxonomies = taxonomies;
        this.taxonomyValues = taxonomyValues;
    }

    @Transactional(readOnly = true)
    public MetadataOptionsView options() {
        Map<String, List<TaxonomyOption>> result = new LinkedHashMap<>();
        for (TaxonomyEntity tax : taxonomies.findAllByOrderById()) {
            List<TaxonomyValueEntity> values = taxonomyValues.findByTaxonomyIdOrderBySortOrder(tax.getId());
            Map<Long, String> keyById = new HashMap<>();
            values.forEach(v -> keyById.put(v.getId(), v.getValueKey()));
            result.put(tax.getTaxonomyKey(), values.stream()
                    .map(v -> new TaxonomyOption(v.getValueKey(), v.getDisplayName(),
                            v.getParentId() == null ? null : keyById.get(v.getParentId()),
                            v.getStatus()))
                    .toList());
        }
        return new MetadataOptionsView("v1", result);
    }
}
