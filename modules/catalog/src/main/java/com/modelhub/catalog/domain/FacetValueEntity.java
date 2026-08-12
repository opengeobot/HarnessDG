package com.modelhub.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** 通用 facet 投影（03 §3.4）：服务端按 facet_definitions 生成，客户端不得直写。 */
@Entity
@Table(name = "repository_facet_values")
public class FacetValueEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "repository_id", nullable = false)
    private Long repositoryId;

    @Column(name = "type_key", nullable = false)
    private String typeKey;

    @Column(name = "schema_version", nullable = false)
    private int schemaVersion;

    @Column(name = "facet_key", nullable = false)
    private String facetKey;

    @Column(name = "value_type", nullable = false)
    private String valueType;

    @Column(name = "value_text")
    private String valueText;

    @Column(name = "value_number")
    private BigDecimal valueNumber;

    @Column(name = "value_boolean")
    private Boolean valueBoolean;

    @Column(nullable = false)
    private int ordinal;

    public Long getId() { return id; }
    public Long getRepositoryId() { return repositoryId; }
    public void setRepositoryId(Long repositoryId) { this.repositoryId = repositoryId; }
    public String getTypeKey() { return typeKey; }
    public void setTypeKey(String typeKey) { this.typeKey = typeKey; }
    public int getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(int schemaVersion) { this.schemaVersion = schemaVersion; }
    public String getFacetKey() { return facetKey; }
    public void setFacetKey(String facetKey) { this.facetKey = facetKey; }
    public String getValueType() { return valueType; }
    public void setValueType(String valueType) { this.valueType = valueType; }
    public String getValueText() { return valueText; }
    public void setValueText(String valueText) { this.valueText = valueText; }
    public BigDecimal getValueNumber() { return valueNumber; }
    public void setValueNumber(BigDecimal valueNumber) { this.valueNumber = valueNumber; }
    public Boolean getValueBoolean() { return valueBoolean; }
    public void setValueBoolean(Boolean valueBoolean) { this.valueBoolean = valueBoolean; }
    public int getOrdinal() { return ordinal; }
    public void setOrdinal(int ordinal) { this.ordinal = ordinal; }
}
