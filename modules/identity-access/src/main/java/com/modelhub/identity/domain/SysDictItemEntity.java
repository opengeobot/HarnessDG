package com.modelhub.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 通用字典项（V17）：item_value 字典内唯一且不可改；标签按语言列存储。 */
@Entity
@Table(name = "sys_dict_item")
public class SysDictItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dict_id", nullable = false)
    private Long dictId;

    @Column(name = "item_value", nullable = false)
    private String itemValue;

    @Column(name = "label_zh", nullable = false)
    private String labelZh;

    @Column(name = "label_en", nullable = false)
    private String labelEn;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private String status = "active";

    private String remark;

    public Long getId() { return id; }
    public Long getDictId() { return dictId; }
    public void setDictId(Long dictId) { this.dictId = dictId; }
    public String getItemValue() { return itemValue; }
    public void setItemValue(String itemValue) { this.itemValue = itemValue; }
    public String getLabelZh() { return labelZh; }
    public void setLabelZh(String labelZh) { this.labelZh = labelZh; }
    public String getLabelEn() { return labelEn; }
    public void setLabelEn(String labelEn) { this.labelEn = labelEn; }
    public int getSortOrder() { return sortOrder; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
