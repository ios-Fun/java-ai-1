package com.changgeng.service;

import com.changgeng.client.DamCoreClient;
import com.changgeng.client.FastgptClient;
import com.changgeng.config.RagConfig;
import com.changgeng.handler.InfluxDBServiceJR;
import com.changgeng.mapper.DefectIncidentInfoMapper;
import com.changgeng.model.DefectIncidentInfo;
import com.changgeng.pojo.IdObj;
import com.changgeng.pojo.InfluxQueryResult;
import com.changgeng.tool.CommonTool;
import com.changgeng.tool.InstanceQueryParam;
import com.changgeng.tree.MultiTreeNode;
import com.changgeng.tree.TreeNodeService;
import com.changgeng.tree.TreeNodeValue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class DeviceHealthyService {

    @Autowired
    RagConfig ragConfig;

    @Autowired
    DamCoreClient damCoreClient;

    @Autowired
    FastgptClient fastgptClient;

    @Autowired
    TreeNodeService treeNodeService;

    @Autowired
    DefectIncidentInfoMapper defectIncidentInfoMapper;

    @Autowired
    InfluxDBServiceJR influxDBServiceJR;

    public String deviceRag(@RequestParam String tagName) {
        log.info("tagName: {}", tagName);
        Map<String, Object> tag_map = new HashMap<>();
        tag_map.put("datasetId", ragConfig.getDatasetId());
        tag_map.put("text", tagName);
        tag_map.put("searchMode", "embedding");
        tag_map.put("embeddingWeight", ragConfig.getEmbeddingWeight());
        tag_map.put("usingReRank", false);
        tag_map.put("limit", ragConfig.getLimit());
        tag_map.put("similarity", ragConfig.getSimilarity());
        tag_map.put("datasetSearchUsingExtensionQuery", false);
        tag_map.put("datasetSearchExtensionModel", ragConfig.getDatasetSearchExtensionModel());
        tag_map.put("datasetSearchExtensionBg", "");
        Map rootMap = fastgptClient.searchTest(tag_map);
        // 过滤下内容
        StringBuilder resultSB = new StringBuilder();
        Map<String, Object> dataMap = (Map<String, Object>) rootMap.get("data");
        List<Map<String, Object>> list = (List<Map<String, Object>>) dataMap.get("list");


        if (list != null && list.size() > 0) {
            // 遍历list，提取id、q
            resultSB.append("## 可以使用<Cites> </Cites> 中的内容作为本次回答的参考\n");
            resultSB.append("<Cites>\n");
            for (int i = 0; i < list.size(); i++) {
                resultSB.append("{\n    ");
                resultSB.append('"');
                resultSB.append("content:");
                resultSB.append('"');
                resultSB.append(':');
                resultSB.append('"');

                resultSB.append(list.get(i).get("q"));
                resultSB.append("\n");
                resultSB.append('"');
                resultSB.append("\n");
                resultSB.append("}\n");
                if (i != list.size() - 1) {
                    resultSB.append("------\n");
                }
            }
            resultSB.append("</Cites>");
        }

        return resultSB.toString();
    }

    public String[] deviceGraphShow(@RequestParam Integer nodeId) {
        String[] returnStrs = new String[2];
        IdObj debutObj = new IdObj();
        debutObj.setId(Long.valueOf(nodeId));
        debutObj.setType("Model");
        Map map = (Map) damCoreClient.debut(debutObj).get("data");
        Integer id = (Integer) map.get("id");
        MultiTreeNode root = new MultiTreeNode();
        TreeNodeValue tagTreeNodeValue = new TreeNodeValue();
        tagTreeNodeValue.setValue(map);
        tagTreeNodeValue.setType(0);
        root.setData(tagTreeNodeValue);

        root.setChildren(treeNodeService.getTreeNodeChildren(Long.valueOf(id)));
        // 按层次遍历多叉树
        StringBuilder sb = new StringBuilder();
        sb.append("## 该设备关联的：故障模式、特征、测点层级图如下，其中测点是传感器的实际值。");
        sb.append("\n");
        StringBuilder sbReturn =  treeNodeService.printMarkdownTree(root, 0);
        if (sbReturn != null) {
            sb.append(sbReturn);
        }
        returnStrs[0] = sb.toString();

        StringBuilder tagsStringBuilder = new StringBuilder();
        // 获取测点
        List list = treeNodeService.getTags(root);
        Set<String> set = new HashSet<>(list);
        int index = 0;
        Iterator<String> it = set.iterator();
        while (it.hasNext()) {
            String item = it.next();
            tagsStringBuilder.append(item);
            if (index != set.size() - 1) {
                tagsStringBuilder.append(",");
            }
            // System.out.println("下标：" + index + "，值：" + item);
            index++;
        }
        returnStrs[1] = tagsStringBuilder.toString();
        return returnStrs;
    }

    public Map<String, InfluxQueryResult> incidentTagsTrend(String assetName) {
        Map<String, InfluxQueryResult> res = new HashMap<>();
        List<DefectIncidentInfo> defectIncidentInfos = defectIncidentInfoMapper.selectOpenedIncidentAssetName();
        List<String> collect = defectIncidentInfos.stream().map(item -> item.getName()).collect(Collectors.toList());
        String bestMatchingStr = CommonTool.getBestMatchingStr(collect, assetName);
        Optional<DefectIncidentInfo> first = defectIncidentInfos.stream().filter(one -> one.getName().equals(bestMatchingStr)).findFirst();
        if (first.isPresent()) {
            DefectIncidentInfo defectIncidentInfo = first.get();
            Long nodeId = defectIncidentInfo.getNodeId();
            List<DefectIncidentInfo> defectIncidentInfos1 = defectIncidentInfoMapper.selectOpenedIncidentAssetTagsByAssetId(nodeId);
            if (defectIncidentInfos1 != null && !defectIncidentInfos1.isEmpty()) {
                for (DefectIncidentInfo defectIncidentInfo1 : defectIncidentInfos1) {
                    Long subsystemId = defectIncidentInfo1.getSubsystemId();
                    String tagCode = defectIncidentInfo1.getTagCode();
                    Date endTime = defectIncidentInfo1.getLastTime();
                    Date beginTime = new Date(endTime.getTime() - 8 * 60 * 60 * 1000);
                    InfluxQueryResult influxQueryResult = influxDBServiceJR.queryValuesV2(tagCode, "RealTimeData_" + subsystemId, beginTime, endTime, "1m", true, true);
                    res.put(tagCode, influxQueryResult);
                }
            }
        }
        return res;
    }

    public Map incidentTagsAttr(String assetName) {
        Map<String, Object> res = new HashMap<>();
        Map<String, Object> tagAttrMaps = new HashMap<>();
        List<DefectIncidentInfo> defectIncidentInfos = defectIncidentInfoMapper.selectOpenedIncidentAssetName();
        List<String> collect = defectIncidentInfos.stream().map(item -> item.getName()).collect(Collectors.toList());
        String bestMatchingStr = CommonTool.getBestMatchingStr(collect, assetName);
        Optional<DefectIncidentInfo> first = defectIncidentInfos.stream().filter(one -> one.getName().equals(bestMatchingStr)).findFirst();
        if (first.isPresent()) {
            DefectIncidentInfo defectIncidentInfo = first.get();
            Long nodeId = defectIncidentInfo.getNodeId();
            List<DefectIncidentInfo> defectIncidentInfos1 = defectIncidentInfoMapper.selectOpenedIncidentAssetTagsByAssetId(nodeId);
            if (defectIncidentInfos1 != null && !defectIncidentInfos1.isEmpty()) {
                for (DefectIncidentInfo defectIncidentInfo1 : defectIncidentInfos1) {
                    Long tagId = defectIncidentInfo1.getNodeId();
                    InstanceQueryParam instanceQueryParam = new InstanceQueryParam();
                    instanceQueryParam.setNodeId(tagId);
                    Map map = damCoreClient.querySelectIgnoreDistanceByCondition(instanceQueryParam);
                    for (Object o : (List) map.get("data")) {
                        Map o1 = (Map) o;
                        Map map1 = (Map) o1.get("properties");
                        Map<String, Object> map2 = new HashMap<>();
                        map2.put("高报", map1.get("高报") == null ? "" : map1.get("高报"));
                        map2.put("高高报", map1.get("高高报") == null ? "" : map1.get("高高报"));
                        map2.put("低报", map1.get("低报") == null ? "" : map1.get("低报"));
                        map2.put("低低报", map1.get("低低报") == null ? "" : map1.get("低低报"));
                        map2.put("高报严重度", map1.get("高报严重度") == null ? "" : map1.get("高报严重度"));
                        map2.put("高高报严重度", map1.get("高高报严重度") == null ? "" : map1.get("高高报严重度"));
                        map2.put("低报严重度", map1.get("低报严重度") == null ? "" : map1.get("低报严重度"));
                        map2.put("低低报严重度", map1.get("低低报严重度") == null ? "" : map1.get("低低报严重度"));
                        map2.put("源标签点描述", map1.get("源标签点描述") == null ? "" : map1.get("源标签点描述"));
                        tagAttrMaps.put(defectIncidentInfo1.getTagCode(), map2);
                    }
                }
            }
        }
        res.put("测点属性", tagAttrMaps);

        String rule = "严重度计算逻辑\n" +
                "1. 基础规则\n" +
                "残差 = 真实值 - 估计值。按残差正负分为正/负偏差路径。\n" +
                "2. 核心算法（平滑过渡）\n" +
                "区间内插值统一采用：Sigmoid变换 → 线性插值 → Logit还原。\n" +
                "3. 正偏差计算（残差 ≥ 0）\n" +
                "失效/越界：高报/高高报/正偏差阈值任一为空则严重度为 0；若 真实值 ≥ 高高报 为 10，若 真实值 < 基准点 为 0。\n" +
                "反向惩罚：若 低低报 > 真实值 为 -10；若 低报 > 真实值 为 -7。\n" +
                "4. 负偏差计算（残差 < 0）\n" +
                "失效/越界：低报/低低报/负偏差阈值任一为空则严重度为 0；若 真实值 ≤ 低低报 为 -10，若 真实值 > 基准点 为 0。\n" +
                "反向惩罚：若 高高报 < 真实值 为 10；若 高报 < 真实值 为 7。";
        res.put("严重度计算逻辑", rule);
        return res;
    }
}
