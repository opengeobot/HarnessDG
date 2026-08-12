package com.modelhub.catalog.domain;

import java.io.Serializable;
import java.util.Objects;

/** SchemaVersionEntity 复合主键（type_key, version）。 */
public class SchemaVersionId implements Serializable {

    private String typeKey;
    private Integer version;

    public SchemaVersionId() {}

    public SchemaVersionId(String typeKey, Integer version) {
        this.typeKey = typeKey;
        this.version = version;
    }

    public String getTypeKey() { return typeKey; }
    public Integer getVersion() { return version; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SchemaVersionId that)) return false;
        return Objects.equals(typeKey, that.typeKey) && Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(typeKey, version);
    }
}
