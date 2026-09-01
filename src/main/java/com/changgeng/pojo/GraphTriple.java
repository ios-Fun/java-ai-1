package com.changgeng.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// 输出的三元组结构
@Data
public class GraphTriple {
    // 节点集合
    private Set<NodeInfo> nodes = new LinkedHashSet<>();
    // 边集合
    private Set<EdgeInfo> edges = new LinkedHashSet<>();

    @Data
    @AllArgsConstructor
    @EqualsAndHashCode(of = "id")  // 用 id 去重
    public static class NodeInfo {
        private Long id;
        private String name;
        private List<String> labels;
        private Map<String, Object> properties;
    }

    @Data
    @AllArgsConstructor
    @EqualsAndHashCode(of = {"childId", "parentId"})  // 用起止点去重
    public static class EdgeInfo {
        private Long childId;
        private Long parentId;
        private String relation;   // 来自 parentIds.status
        private String weight;     // 来自 parentIds.weight 或 node.weight
    }
}