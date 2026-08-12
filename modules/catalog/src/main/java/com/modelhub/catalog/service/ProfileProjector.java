package com.modelhub.catalog.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modelhub.catalog.domain.DatasetProfileEntity;
import com.modelhub.catalog.domain.FacetValueEntity;
import com.modelhub.catalog.domain.ModelProfileEntity;
import com.modelhub.catalog.domain.RepositoryEntity;
import com.modelhub.catalog.domain.SchemaVersionEntity;
import com.modelhub.catalog.domain.StudioProfileEntity;
import com.modelhub.catalog.domain.TaxonomyEntity;
import com.modelhub.catalog.domain.TaxonomyValueEntity;
import com.modelhub.catalog.repo.DatasetProfileRepository;
import com.modelhub.catalog.repo.FacetValueRepository;
import com.modelhub.catalog.repo.ModelProfileRepository;
import com.modelhub.catalog.repo.StudioProfileRepository;
import com.modelhub.catalog.repo.TaxonomyRepository;
import com.modelhub.catalog.repo.TaxonomyValueRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 投影生成器（03 §3.4）：服务端按 facet_definitions 从已校验 metadata 生成
 * repository_facet_values 与类型 profile；客户端不得直写投影表。
 */
@Component
public class ProfileProjector {

    private final ObjectMapper objectMapper;
    private final FacetValueRepository facetValues;
    private final ModelProfileRepository modelProfiles;
    private final DatasetProfileRepository datasetProfiles;
    private final StudioProfileRepository studioProfiles;
    private final TaxonomyRepository taxonomies;
    private final TaxonomyValueRepository taxonomyValues;

    public ProfileProjector(ObjectMapper objectMapper, FacetValueRepository facetValues,
                            ModelProfileRepository modelProfiles, DatasetProfileRepository datasetProfiles,
                            StudioProfileRepository studioProfiles, TaxonomyRepository taxonomies,
                            TaxonomyValueRepository taxonomyValues) {
        this.objectMapper = objectMapper;
        this.facetValues = facetValues;
        this.modelProfiles = modelProfiles;
        this.datasetProfiles = datasetProfiles;
        this.studioProfiles = studioProfiles;
        this.taxonomies = taxonomies;
        this.taxonomyValues = taxonomyValues;
    }

    /** metadata 变更后重建投影（先删后插，幂等）。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void project(RepositoryEntity repo, SchemaVersionEntity schema, JsonNode metadata) {
        facetValues.deleteByRepositoryId(repo.getId());
        projectFacets(repo, schema, metadata);
        switch (repo.getResourceType()) {
            case "model" -> projectModel(repo.getId(), metadata);
            case "dataset" -> projectDataset(repo.getId(), metadata);
            case "studio" -> projectStudio(repo.getId(), metadata);
            default -> { }
        }
    }

    private void projectFacets(RepositoryEntity repo, SchemaVersionEntity schema, JsonNode metadata) {
        JsonNode defs;
        try {
            defs = objectMapper.readTree(schema.getFacetDefinitions());
        } catch (Exception e) {
            return;
        }
        if (!defs.isArray()) {
            return;
        }
        for (JsonNode def : defs) {
            String key = def.path("key").asText();
            String type = def.path("type").asText("text");
            // facet 值来源：metadata 中与 facet key 对应的字段（tag←tags、scene←scenes、capability←capabilities、dataFormat←dataFormats）
            String source = facetSourceField(key);
            JsonNode value = metadata.get(source);
            if (value == null || value.isNull()) {
                continue;
            }
            if (value.isArray()) {
                int ordinal = 0;
                for (JsonNode item : value) {
                    writeFacet(repo, schema, key, type, item, ordinal++);
                }
            } else {
                writeFacet(repo, schema, key, type, value, 0);
            }
        }
    }

    private static String facetSourceField(String facetKey) {
        return switch (facetKey) {
            case "tag" -> "tags";
            case "scene" -> "scenes";
            case "capability" -> "capabilities";
            case "dataFormat" -> "dataFormats";
            default -> facetKey;
        };
    }

    private void writeFacet(RepositoryEntity repo, SchemaVersionEntity schema, String key, String type,
                            JsonNode value, int ordinal) {
        FacetValueEntity f = new FacetValueEntity();
        f.setRepositoryId(repo.getId());
        f.setTypeKey(repo.getResourceType());
        f.setSchemaVersion(schema.getVersion());
        f.setFacetKey(key);
        f.setOrdinal(ordinal);
        switch (type) {
            case "number" -> {
                f.setValueType("number");
                f.setValueNumber(value.isNumber() ? new BigDecimal(value.asText()) : null);
            }
            case "boolean" -> {
                f.setValueType("boolean");
                f.setValueBoolean(value.isBoolean() ? value.asBoolean() : null);
            }
            default -> {
                f.setValueType("text");
                String text = value.asText();
                f.setValueText(text.length() > 256 ? text.substring(0, 256) : text);
            }
        }
        facetValues.save(f);
    }

    private void projectModel(Long repoId, JsonNode metadata) {
        ModelProfileEntity p = modelProfiles.findById(repoId).orElseGet(() -> {
            ModelProfileEntity n = new ModelProfileEntity();
            n.setRepositoryId(repoId);
            return n;
        });
        p.setTaskValueId(taxonomyValueId("model_task", textOrNull(metadata, "task")));
        p.setArchitectureValueId(taxonomyValueId("architecture", textOrNull(metadata, "architecture")));
        p.setPrimaryLanguageValueId(taxonomyValueId("language", textOrNull(metadata, "language")));
        p.setApiStatus(textOrNull(metadata, "apiStatus"));
        p.setParameterCount(metadata.hasNonNull("parameterCount") ? metadata.get("parameterCount").asLong() : null);
        p.setParameterUnit(textOrNull(metadata, "parameterUnit"));
        p.setDeployable(metadata.path("deployable").asBoolean(false));
        p.setMcpCompatible(metadata.path("mcpCompatible").asBoolean(false));
        modelProfiles.save(p);
    }

    private void projectDataset(Long repoId, JsonNode metadata) {
        DatasetProfileEntity p = datasetProfiles.findById(repoId).orElseGet(() -> {
            DatasetProfileEntity n = new DatasetProfileEntity();
            n.setRepositoryId(repoId);
            return n;
        });
        p.setTaskValueId(taxonomyValueId("dataset_task", textOrNull(metadata, "task")));
        p.setEstimatedRows(metadata.hasNonNull("estimatedRows") ? metadata.get("estimatedRows").asLong() : null);
        p.setSensitivityLevel(textOrNull(metadata, "sensitivityLevel"));
        p.setPreviewPolicy(textOrNull(metadata, "previewPolicy"));
        datasetProfiles.save(p);
    }

    private void projectStudio(Long repoId, JsonNode metadata) {
        StudioProfileEntity p = studioProfiles.findById(repoId).orElseGet(() -> {
            StudioProfileEntity n = new StudioProfileEntity();
            n.setRepositoryId(repoId);
            return n;
        });
        p.setRuntimeType(textOrNull(metadata, "runtimeType"));
        studioProfiles.save(p);
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private Long taxonomyValueId(String taxonomyKey, String valueKey) {
        if (valueKey == null) {
            return null;
        }
        TaxonomyEntity tax = taxonomies.findByTaxonomyKey(taxonomyKey).orElse(null);
        if (tax == null) {
            return null;
        }
        return taxonomyValues.findByTaxonomyIdOrderBySortOrder(tax.getId()).stream()
                .filter(v -> valueKey.equals(v.getValueKey()))
                .map(TaxonomyValueEntity::getId)
                .findFirst().orElse(null);
    }

    /** profile 清理（仓库 purge 阶段使用）。 */
    public void deleteProjections(Long repositoryId) {
        facetValues.deleteByRepositoryId(repositoryId);
        modelProfiles.deleteById(repositoryId);
        datasetProfiles.deleteById(repositoryId);
        studioProfiles.deleteById(repositoryId);
    }

    public List<FacetValueEntity> facetsOf(Long repositoryId) {
        return facetValues.findByRepositoryId(repositoryId);
    }
}
