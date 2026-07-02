/*
 * 功能: 字典治理字段校验端口，供资产等模块校验治理字段引用的字典项合法性与可引用性。
 * 时间: 2026-06-30
 * 作者: AxeXie
 */
package com.aihub.taxonomy.dictionary.application;

/**
 * 字典治理字段校验端口。
 *
 * <p>供资产模块（Task 14）在写入治理字段（license/framework/task/format/modality 等）时校验：
 * <ul>
 *   <li>{@link #isKnown}：含停用项，用于历史资产回显（停用项仍认可其曾合法）。</li>
 *   <li>{@link #validateItemCode}：仅 ACTIVE 项通过，用于<b>新建引用</b>校验（停用项不可新建引用）。</li>
 * </ul>
 */
public interface DictionaryValidationPort {

    /**
     * 字典项是否已知（含停用，用于回显）。
     *
     * @param dictCode 字典编码
     * @param itemCode 字典项编码
     * @return true 表示存在（ACTIVE 或 DISABLED）
     */
    boolean isKnown(String dictCode, String itemCode);

    /**
     * 校验字典项可用于新建引用：必须存在且 ACTIVE，否则抛出 ValidationException。
     *
     * @param dictCode 字典编码
     * @param itemCode 字典项编码
     */
    void validateItemCode(String dictCode, String itemCode);
}
