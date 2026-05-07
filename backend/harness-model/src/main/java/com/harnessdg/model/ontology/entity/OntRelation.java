package com.harnessdg.model.ontology.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.harnessdg.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ont_relation")
public class OntRelation extends BaseEntity {

    private Long sourceEntityId;

    private Long targetEntityId;

    private String relationType;

    private String joinCondition;

    private String description;

    private String status;
}
