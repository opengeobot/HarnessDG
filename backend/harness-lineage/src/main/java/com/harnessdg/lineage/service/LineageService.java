/**
 * 功能：血缘 Service 接口
 * 时间：2026-05-08
 * 作者：AxeXie
 */
package com.harnessdg.lineage.service;

import com.harnessdg.model.lineage.dto.LineageCreateRequest;
import com.harnessdg.model.lineage.dto.LineageEdgeDTO;
import com.harnessdg.model.lineage.dto.LineageNodeDTO;

import java.util.List;

public interface LineageService {

    List<LineageNodeDTO> listNodes(String nodeType, Long entityId);

    LineageNodeDTO getNode(Long id);

    List<LineageEdgeDTO> listEdges(Long nodeId);

    void createLineage(LineageCreateRequest request);

    void deleteLineage(Long nodeId);
}
