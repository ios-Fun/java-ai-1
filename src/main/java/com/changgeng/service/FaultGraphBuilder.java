package com.changgeng.service;
import com.changgeng.client.DamExtClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.changgeng.client.DamCoreClient;
import com.changgeng.config.FeignResultConverter;
import com.changgeng.pojo.ApiResponse;
import com.changgeng.pojo.FaultNodeDTO;
import com.changgeng.pojo.GraphTriple;
import com.changgeng.pojo.IdObj;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class FaultGraphBuilder {

    @Autowired
    DamCoreClient damCoreClient;

    @Autowired
    DamExtClient damExtClient;

    @Autowired
    FeignResultConverter converter;

    /**
     * 入口方法：从指定根节点开始，递归展开整棵树，返回三元组结构
     */
    public GraphTriple buildGraph(Long rootNodeId) {
        GraphTriple result = new GraphTriple();
        Set<Long> visited = new HashSet<>();  // 防止循环引用导致死递归
        expandNode(rootNodeId, null, result, visited, true);
        return result;
    }

    /**
     * 递归展开单个节点
     * @param nodeId      当前要展开的节点 ID
     * @param parentId    父节点 ID（用于建立边），根节点传 null
     * @param result      累积结果
     * @param visited     已访问节点集合，防环
     */
    private void expandNode(Long nodeId, Long parentId, GraphTriple result, Set<Long> visited, Boolean isHeader) {
        if (nodeId == null || visited.contains(nodeId)) {
            return;  // 空节点或已访问过，跳过
        }
        visited.add(nodeId);

        // 1. 调用接口获取该节点的子节点列表
        ApiResponse response = callApi(nodeId, isHeader);
        if (response == null || !Boolean.TRUE.equals(response.getSuccess())
                || response.getData() == null || ((List)response.getData()).isEmpty()) {
            return;  // 无子节点或调用失败，终止此分支
        }

        // 2. 遍历返回的子节点，收集节点和边
        for (FaultNodeDTO child : (List<FaultNodeDTO>) response.getData()) {
            // 添加节点信息
            result.getNodes().add(new GraphTriple.NodeInfo(
                    child.getId(),
                    child.getName(),
                    child.getLabel(),
                    child.getProperties()
            ));

            // 添加边信息：从 parentIds 中提取关系和权重
            if (child.getParentIds() != null && !child.getParentIds().isEmpty()) {
                for (FaultNodeDTO.ParentLink link : child.getParentIds()) {
                    // link.id 可能是父节点 ID，也可能是自身 ID，需根据你的业务语义调整
                    Long actualParentId = link.getId();
                    String relation = link.getStatus();
                    String edgeWeight = link.getWeight();

                    // 如果 parentIds 中的 id 不是真正的父节点，则使用传入的 parentId
                    if (actualParentId == null || actualParentId.equals(child.getId())) {
                        actualParentId = parentId;
                    }

                    if (actualParentId != null) {
                        result.getEdges().add(new GraphTriple.EdgeInfo(
                                child.getId(),
                                actualParentId,
                                relation,
                                edgeWeight
                        ));
                    }
                }
            } else if (parentId != null) {
                // 如果没有 parentIds 但有明确的父节点，直接用节点自身的 weight/status 建边
                result.getEdges().add(new GraphTriple.EdgeInfo(
                        child.getId(),
                        parentId,
                        child.getStatus(),
                        child.getWeight()
                ));
            }

            // 3. 递归展开该子节点
            expandNode(child.getId(), child.getId(), result, visited, false);
        }
    }

    /**
     * 调用远程接口
     */
    private ApiResponse callApi(Long nodeId, Boolean isHeader) {
        try {
            // Feign 返回 Object，安全转换为 ApiResponse
            IdObj debutObj = new IdObj();
            debutObj.setId(Long.valueOf(nodeId));
            debutObj.setType("Model");
            Object raw = new Object();
            if (isHeader) {
                raw = damCoreClient.debut(debutObj);
            }
            else {
                raw = damCoreClient.unfold(debutObj);
            }

            // 第一步：转为宽松的 ApiResponse（data 为 Object）
            ApiResponse resp = converter.convert(raw, ApiResponse.class);

            // 第二步：将 data 安全转为 List<FaultNodeDTO>
            if (resp != null && resp.getData() != null) {
                List<FaultNodeDTO> rootNodes = converter.extractNodeInfos(resp.getData(), FaultNodeDTO.class);
                resp.setData(rootNodes);  // 注意：此时需将 data 改回 List 类型，或用新变量承载
            }
            return resp;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public String toLlmPromptString(GraphTriple triple) {
        StringBuilder sb = new StringBuilder();

        // 1. 节点表格
        sb.append("## 节点列表\n");
        sb.append("| ID | 名称 | 标签 | 关键属性 |\n");
        sb.append("|---|---|---|---|\n");
        for (GraphTriple.NodeInfo node : triple.getNodes()) {
            String labels = String.join(", ", node.getLabels());
            // 只提取关键属性，避免无关字段干扰模型
            String props = node.getProperties() == null ? "" :
                    node.getProperties().entrySet().stream()
                            .filter(e -> e.getValue() != null)
                            .map(e -> e.getKey() + "=" + e.getValue())
                            .collect(Collectors.joining("; "));
            if (labels.contains("特征")) {
                List<Map> basicTagListByShadowFeatureId = damExtClient.getBasicTagListByShadowFeatureId(node.getId());
                if (!basicTagListByShadowFeatureId.isEmpty()) {
                    props = props + " 表征测点：" + basicTagListByShadowFeatureId.toString();
                }
            }
            sb.append(String.format("| %d | %s | %s | %s |\n",
                    node.getId(), node.getName(), labels, props));
        }

        // 2. 边表格
        sb.append("\n## 关系列表\n");
        sb.append("| 子节点ID | 父节点ID | 关系类型 | 权重 |\n");
        sb.append("|---|---|---|---|\n");
        for (GraphTriple.EdgeInfo edge : triple.getEdges()) {
            sb.append(String.format("| %d | %d | %s | %s |\n",
                    edge.getChildId(), edge.getParentId(),
                    edge.getRelation() == null ? "-" : edge.getRelation(),
                    edge.getWeight() == null ? "-" : edge.getWeight()));
        }

        // 3. 语义摘要（关键！帮助模型理解整体结构）
        sb.append("\n## 结构摘要\n");
        sb.append(String.format("该故障树共包含 %d 个节点和 %d 条因果关系边。\n",
                triple.getNodes().size(), triple.getEdges().size()));
        sb.append("\n## 部分说明\n");
        sb.append("该图结构，是头节点指代的故障模型的触发条件，当满足连线与节点关系时，就会触发对应的故障模式，每个节点均会根据自身条件向父节点输入0或1的结果，如果最终根节点接收到1，则触发对应故障模式报警。\n");
        // 可在此处追加根节点、叶子节点统计等高层信息

        return sb.toString();
    }
}