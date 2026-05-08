/**
 * 功能：血缘 Service 实现
 * 时间：2026-05-08
 * 作者：AxeXie
 */
package com.harnessdg.lineage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.harnessdg.common.exception.BizException;
import com.harnessdg.common.response.ErrorCode;
import com.harnessdg.lineage.mapper.LineageEdgeMapper;
import com.harnessdg.lineage.mapper.LineageNodeMapper;
import com.harnessdg.lineage.service.LineageService;
import com.harnessdg.model.lineage.dto.LineageCreateRequest;
import com.harnessdg.model.lineage.dto.LineageEdgeDTO;
import com.harnessdg.model.lineage.dto.LineageNodeDTO;
import com.harnessdg.model.lineage.entity.LineageEdge;
import com.harnessdg.model.lineage.entity.LineageNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LineageServiceImpl implements LineageService {

    private final LineageNodeMapper lineageNodeMapper;
    private final LineageEdgeMapper lineageEdgeMapper;

    @Override
    public List<LineageNodeDTO> listNodes(String nodeType, Long entityId) {
        LambdaQueryWrapper<LineageNode> wrapper = new LambdaQueryWrapper<>();
        if (nodeType != null && !nodeType.isBlank()) {
            wrapper.eq(LineageNode::getNodeType, nodeType);
        }
        if (entityId != null) {
            wrapper.eq(LineageNode::getEntityId, entityId);
        }
        wrapper.orderByDesc(LineageNode::getCreatedAt);
        return lineageNodeMapper.selectList(wrapper).stream()
                .map(this::toNodeDTO)
                .collect(Collectors.toList());
    }

    @Override
    public LineageNodeDTO getNode(Long id) {
        LineageNode entity = lineageNodeMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.LINEAGE_NODE_NOT_FOUND);
        }
        return toNodeDTO(entity);
    }

    @Override
    public List<LineageEdgeDTO> listEdges(Long nodeId) {
        LambdaQueryWrapper<LineageEdge> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LineageEdge::getSourceNodeId, nodeId)
                .or()
                .eq(LineageEdge::getTargetNodeId, nodeId);
        wrapper.orderByDesc(LineageEdge::getCreatedAt);
        return lineageEdgeMapper.selectList(wrapper).stream()
                .map(this::toEdgeDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void createLineage(LineageCreateRequest request) {
        // 先插入所有节点
        for (LineageCreateRequest.NodeRequest nodeReq : request.getNodes()) {
            LineageNode node = new LineageNode();
            node.setNodeType(nodeReq.getNodeType());
            node.setNodeKey(nodeReq.getNodeKey());
            node.setName(nodeReq.getName());
            node.setEntityId(nodeReq.getEntityId());
            node.setMetricId(nodeReq.getMetricId());
            node.setDataSource(nodeReq.getDataSource());
            node.setExtra(nodeReq.getExtra());
            lineageNodeMapper.insert(node);
        }

        // 如果有边关系，插入边
        if (request.getEdges() != null && !request.getEdges().isEmpty()) {
            for (LineageCreateRequest.EdgeRequest edgeReq : request.getEdges()) {
                LineageEdge edge = new LineageEdge();
                edge.setSourceNodeId(edgeReq.getSourceNodeId());
                edge.setTargetNodeId(edgeReq.getTargetNodeId());
                edge.setEdgeType(edgeReq.getEdgeType());
                edge.setTransformLogic(edgeReq.getTransformLogic());
                edge.setExtra(edgeReq.getExtra());
                lineageEdgeMapper.insert(edge);
            }
        }
    }

    @Override
    @Transactional
    public void deleteLineage(Long nodeId) {
        LineageNode entity = lineageNodeMapper.selectById(nodeId);
        if (entity == null) {
            throw new BizException(ErrorCode.LINEAGE_NODE_NOT_FOUND);
        }

        // 删除与该节点相关的所有边
        LambdaQueryWrapper<LineageEdge> edgeWrapper = new LambdaQueryWrapper<>();
        edgeWrapper.eq(LineageEdge::getSourceNodeId, nodeId)
                .or()
                .eq(LineageEdge::getTargetNodeId, nodeId);
        lineageEdgeMapper.delete(edgeWrapper);

        // 删除节点
        lineageNodeMapper.deleteById(nodeId);
    }

    private LineageNodeDTO toNodeDTO(LineageNode entity) {
        LineageNodeDTO dto = new LineageNodeDTO();
        dto.setId(entity.getId());
        dto.setNodeType(entity.getNodeType());
        dto.setNodeKey(entity.getNodeKey());
        dto.setName(entity.getName());
        dto.setEntityId(entity.getEntityId());
        dto.setMetricId(entity.getMetricId());
        dto.setDataSource(entity.getDataSource());
        dto.setExtra(entity.getExtra());
        return dto;
    }

    private LineageEdgeDTO toEdgeDTO(LineageEdge entity) {
        LineageEdgeDTO dto = new LineageEdgeDTO();
        dto.setId(entity.getId());
        dto.setSourceNodeId(entity.getSourceNodeId());
        dto.setTargetNodeId(entity.getTargetNodeId());
        dto.setEdgeType(entity.getEdgeType());
        dto.setTransformLogic(entity.getTransformLogic());
        dto.setExtra(entity.getExtra());
        return dto;
    }
}
