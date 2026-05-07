package com.harnessdg.dictionary.service;

import com.harnessdg.model.dict.dto.*;

import java.util.List;
import java.util.Map;

public interface DictService {

    List<DictGroupDTO> listGroups(String category, String status);

    DictGroupDTO getGroupByCode(String code, String locale);

    DictGroupDTO createGroup(DictGroupCreateRequest request);

    DictGroupDTO updateGroup(String code, DictGroupUpdateRequest request);

    void deleteGroup(String code);

    List<DictItemDTO> listItems(String groupCode, String locale);

    List<DictItemDTO> getItemTree(String groupCode, String locale);

    DictItemDTO createItem(DictItemCreateRequest request);

    DictItemDTO updateItem(Long id, DictItemUpdateRequest request);

    void deleteItem(Long id);

    List<DictItemDTO> batchCreateItems(String groupCode, List<DictItemCreateRequest> requests);

    /**
     * 批量获取多个字典分组下的所有字典项
     * @param groupCodes 分组 code 列表
     * @param locale 语言（可为空，为空则不做语言解析）
     * @return key = groupCode, value = items
     */
    Map<String, List<DictItemDTO>> batchGetItems(List<String> groupCodes, String locale);

    /**
     * 按关键字搜索字典项（支持按 label/description/code 模糊匹配）
     * @param keyword 关键字
     * @param groupCode 可选限制到指定分组
     * @param locale 语言
     */
    List<DictItemDTO> searchItems(String keyword, String groupCode, String locale);

    /**
     * 导出某分组的字典数据（分组 + 所有项）
     */
    DictExportData exportGroup(String groupCode);

    /**
     * 导入字典数据（分组 + 所有项）
     * @param data 导入数据
     * @param overwrite 是否覆盖同名分组
     */
    DictGroupDTO importGroup(DictExportData data, boolean overwrite);
}
