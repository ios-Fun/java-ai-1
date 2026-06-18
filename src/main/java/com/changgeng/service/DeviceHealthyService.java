package com.changgeng.service;

import com.changgeng.client.DamCoreClient;
import com.changgeng.client.FastgptClient;
import com.changgeng.config.RagConfig;
import com.changgeng.pojo.IdObj;
import com.changgeng.tree.MultiTreeNode;
import com.changgeng.tree.TreeNodeService;
import com.changgeng.tree.TreeNodeValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.*;

@Service
public class DeviceHealthyService {

    @Autowired
    RagConfig ragConfig;

    @Autowired
    DamCoreClient damCoreClient;

    @Autowired
    FastgptClient fastgptClient;

    @Autowired
    TreeNodeService treeNodeService;

    public String deviceRag(@RequestParam String tagName) {
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
}
