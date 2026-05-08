/**
 * 功能：血缘创建请求
 * 时间：2026-05-08
 * 作者：AxeXie
 */
package com.harnessdg.model.lineage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class LineageCreateRequest {

    @NotNull(message = "节点列表不能为空")
    private List<NodeRequest> nodes;

    private List<EdgeRequest> edges;

    @Data
    public static class NodeRequest {
        @NotBlank(message = "nodeType 不能为空")
        private String nodeType;

        @NotBlank(message = "nodeKey 不能为空")
        private String nodeKey;

        private Map<String, String> name;

        private Long entityId;

        private Long metricId;

        private String dataSource;

        private Map<String, Object> extra;
    }

    @Data
    public static class EdgeRequest {
        @NotNull(message = "sourceNodeId 不能为空")
        private Long sourceNodeId;

        @NotNull(message = "targetNodeId 不能为空")
        private Long targetNodeId;

        @NotBlank(message = "edgeType 不能为空")
        private String edgeType;

        private String transformLogic;

        private Map<String, Object> extra;
    }
}
