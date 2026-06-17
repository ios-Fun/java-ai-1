package com.changgeng.tree;

import com.changgeng.client.DamCoreClient;
import com.changgeng.client.DamExtClient;
import com.changgeng.pojo.IdObj;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
public class TreeNodeService {
    @Autowired
    DamCoreClient damCoreClient;

    @Autowired
    DamExtClient damExtClient;

    public List getTreeNodeChildren(Long  id) {

        List children = new ArrayList<>();
        IdObj unfoldObj = new IdObj();
        unfoldObj.setId(Long.valueOf(id));
        List<Object> data1 = (List<Object>) damCoreClient.unfold(unfoldObj).get("data");
        for(Object obj: data1) {
            Map<String, Object> data = (Map<String, Object>) obj;
            // naryTreeNode.setValue(data);
            MultiTreeNode node = new MultiTreeNode();
            TreeNodeValue treeNodeValue = new TreeNodeValue();
            treeNodeValue.setValue(data);
            node.setData(treeNodeValue);
            Integer itemId = (Integer) data.get("id");
            List<String> types = (List<String>) data.get("label");
            if (types.get(0).equals("特征")) {
                treeNodeValue.setType(1);
                // 获取测点--有些问题
                List<Map> items = damExtClient.getTags(itemId);
                List tagChildren = new ArrayList();
                for (Map map : items) {
                    MultiTreeNode tagNode = new MultiTreeNode();
                    TreeNodeValue tagTreeNodeValue = new TreeNodeValue();
                    tagTreeNodeValue.setType(2);
                    tagTreeNodeValue.setValue(map);

                    tagNode.setData(tagTreeNodeValue);
//                    tagNode.setType(2);
//                    tagNode.setValue(map);
                    tagChildren.add(tagNode);
                }
                node.setChildren(tagChildren);
            }else if (types.get(0).equals("故障模式")) {
                treeNodeValue.setType(0);
                node.setChildren(getTreeNodeChildren(Long.valueOf(itemId)));
            }else if (types.get(1).equals("FaultOperator")) {
                treeNodeValue.setType(3);
                node.setChildren(getTreeNodeChildren(Long.valueOf(itemId)));
            }
            else {
                node.setChildren(getTreeNodeChildren(Long.valueOf(itemId)));
            }

            children.add(node);
        }
        return children;
    }

    // 原从上到下层序遍历
    public List<List<MultiTreeNode>> levelOrder(MultiTreeNode root) {
        List<List<MultiTreeNode>> result = new ArrayList<>();
        if (root == null) return result;

        Queue<MultiTreeNode> queue = new LinkedList<>();
        queue.offer(root);

        while (!queue.isEmpty()) {
            int size = queue.size();
            List<MultiTreeNode> level = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                MultiTreeNode curr = queue.poll();
                level.add(curr);
                // queue.addAll(curr.children);
                if (curr.children != null) {
                    for (MultiTreeNode child : curr.children) {
                        queue.offer(child);
                    }
                }
            }
            result.add(level);
        }
        return result;
    }

    public List<String> getTags(MultiTreeNode node) {
        List<String> tagsName = new ArrayList<>();
        if (node == null) return tagsName;
        if (node.getData().getType() != null && node.getData().getType() == 2) {
            Map value = node.data.getValue();
            String name = (String) (value.get("name") == null ? value.get("名称") : value.get("name"));
            tagsName.add(name);
        }
        if (node.children == null) {
            return tagsName;
        }

        for (MultiTreeNode child : node.children) {
            tagsName.addAll(getTags(child));
        }
        return tagsName;
    }

    /**
     * 展示层级关系
     *
     * @param node
     * @return
     */
    public StringBuilder printMarkdownTree(MultiTreeNode node, int level) {
        StringBuilder indentSb = new StringBuilder();
        if (node == null) return indentSb;

        if (node.getData().getType() != null && node.getData().getType() == 3) {
            for (MultiTreeNode child : node.children) {
                // 判断试试特征和测点
                indentSb.append(printMarkdownTree(child, level));
            }
            return indentSb;
        }


        for (int i = 0; i < level; i++) {
            indentSb.append("  ");
        }
        String indent = indentSb.toString();
        Map value = node.data.getValue();
        String name = (String) (value.get("name") == null ? value.get("名称") : value.get("name"));
        log.info("{}- {}{}", indent, getTypeStr(node.getData().getType()), name);
        indentSb.append(String.format("%s- %s%s", indent, getTypeStr(node.getData().getType()), name));
        indentSb.append("\n");
        if (node.children == null) {
            return indentSb;
        }
        for (MultiTreeNode child : node.children) {
            // 判断试试特征和测点

            indentSb.append(printMarkdownTree(child, level + 1));
        }
        return indentSb;
    }

    private String getTypeStr(Integer type){
        if (type == null) {
            return "";
        }
        // 0--故障模式，1--特征，2--测点， 3--操作符
        String typeStr = "";
        if (type == 0) {
            typeStr = "故障模式";
        } else if (type == 1) {
            typeStr = "特征";
        } else if (type == 2) {
            typeStr = "测点";
        }
        return String.format("[%s]", typeStr);
    }

    /**
     * 从下到上 层序遍历
     */
    public String levelOrderBottom(MultiTreeNode root) {
        StringBuilder resultSB = new StringBuilder();
        List<List<MultiTreeNode>> levelList = levelOrder(root);
        // 反转集合，实现从底层到顶层
        List<List<MultiTreeNode>> res = new ArrayList<>();
        for (int i = levelList.size() - 1; i >= 0; i--) {
            res.add(levelList.get(i));
        }

        Map<Integer, String> cacheMap = new HashMap<>();
        // Set<Integer> set = new HashSet<>();
        // 打印下内容
        for (int i = 0; i< res.size();i++) {
            List<MultiTreeNode> list = res.get(i);
            for (int j = 0 ; j<list.size(); j++) {
                MultiTreeNode treeNode = list.get(j);
                TreeNodeValue nodeValue = treeNode.getData();
                Map map = treeNode.getData().getValue();
                Integer id = (Integer) treeNode.getData().getValue().get("id");
                if (!(cacheMap.containsKey(id))) {
                    if (nodeValue.getType() != null && nodeValue.getType() == 2) {
                        // 测点
                        log.info("// realTimeValue:获取实际值, 参数：测点编码");
                        resultSB.append("// realTimeValue:获取实际值, 参数：测点编码");
                        resultSB.append("\n");
//                        Boolean isSwitch = map.get("类别").equals("开关量");
//                        String typeStr = isSwitch ? "Boolean[]": "Double[]";
                        String codeStr = String.format("%s tag_%s = realTimeValue('%s');","Double", map.get("名称"), map.get("编码"));
                        log.info(codeStr);
                        resultSB.append(codeStr);
                        resultSB.append("\n");
                        String codeName = String.format("tag_%s", map.get("名称"));

                        // log.info(codeStr);
                        cacheMap.put(id, codeName);
                    }else if (nodeValue.getType() != null && nodeValue.getType() == 1){
                        // 特征
                        if (treeNode.children != null) {
                            StringBuilder sb = new StringBuilder();
                            for (int a = 0; a < treeNode.getChildren().size(); a++) {
                                Integer childId = (Integer) treeNode.getChildren().get(a).data.getValue().get("id");
                                String tagStr = cacheMap.get(childId);
                                sb.append(tagStr);
                                if (a != treeNode.getChildren().size() - 1) {
                                    sb.append(" || ");
                                }
                            }
                            // String codeStr = String.format("Double attr_%s = %s;", map.get("name"), sb.toString());
                            // log.info(codeStr);
                            String codeName = String.format("tag_%s", map.get("name"));


                            // 权重，状态

                            String attrStr = "";
                            Boolean hasStatus = true;
                            String statusStr = ".";
                            if (map.get("status").equals("")) {
                                hasStatus = false;
                                statusStr = "";
                            }
                            if (map.get("weight").equals("")) {
                                attrStr = String.format("Double attr_%s_%d = %s%s%s;", map.get("name"), map.get("id"), sb.toString(),statusStr, map.get("status"), map.get("weight"));
                            }else {
                                // 快速降低
                                // 状态加上权重信息
                                attrStr = String.format("Double attr_%s_%d = %s%s%s * %s;", map.get("name"), map.get("id"), sb.toString(), statusStr, map.get("status"), map.get("weight"));
                            }

                            log.info(attrStr);
                            resultSB.append(attrStr);
                            resultSB.append("\n");

                            String attrCodeName = String.format("attr_%s_%d", map.get("name"), map.get("id"));
                            cacheMap.put(id, attrCodeName);
                        }

                    }else if (nodeValue.getType() != null && nodeValue.getType() == 3){
                        // 运算符
                        if (treeNode.children != null) {
                            StringBuilder sb = new StringBuilder();
                            String opName = (String) map.get("name");
                            String op_type = " && ";
                            if (opName != null) {
                                if (opName.equals("与")) {
                                    op_type = " && ";
                                } else if (opName.equals("或")) {
                                    op_type = " || ";
                                }else if (opName.equals("等于比较")) {
                                    op_type = " == ";
                                }else if (opName.equals("报警阈")) {

                                }
                                else {
                                    log.warn("other op: {}", opName);
                                }
                                Map propertiesMap = (Map) map.get("properties");
                                for (int b = 0; b < treeNode.getChildren().size(); b++) {
                                    Integer childId = (Integer) treeNode.getChildren().get(b).data.getValue().get("id");
                                    String tagStr = cacheMap.get(childId);
                                    sb.append(tagStr);
                                    if (opName.equals("报警阈")) {
                                        sb.append(getOperation((String) propertiesMap.get("operation")));
                                        sb.append((Integer) propertiesMap.get("operateParameter"));
                                        sb.append(getCompare((String) propertiesMap.get("compare")));
                                        sb.append((String) propertiesMap.get("name"));
                                    }else if (b != treeNode.getChildren().size() - 1) {
                                        sb.append(op_type);
                                    }

                                }
                                String codeStr = String.format("Double op_%s = %s;", map.get("id"), sb.toString());
                                log.info(codeStr);
                                resultSB.append(codeStr);
                                resultSB.append("\n");

                                String opCodeName = String.format("op_%s", map.get("id"));
                                cacheMap.put(id, opCodeName);
                            }else {
                                log.warn(opName);
                            }

                        }
                    }else {
                        // 故障模式
                        if (treeNode.children != null) {
                            StringBuilder sb = new StringBuilder();
                            for (int b = 0; b < treeNode.getChildren().size(); b++) {
                                Integer childId = (Integer) treeNode.getChildren().get(b).data.getValue().get("id");
                                String opStr = cacheMap.get(childId);
                                sb.append(opStr);
                                if (b != treeNode.getChildren().size() - 1) {
                                    sb.append(" && ");
                                }
                            }
                            log.info("// 判断是否发生了故障");
                            resultSB.append("// 判断是否发生了故障");
                            resultSB.append("\n");

                            String defectStr = String.format("Boolean defect_%s = %s;", map.get("name"), sb.toString());
                            log.info(defectStr);
                            resultSB.append(defectStr);
                            resultSB.append("\n");
                        }
                    }
                }
            }
        }
        return resultSB.toString();
    }

    private String getOperation(String operation) {
        if (operation.equals("加")) {
            return " + ";
        } else if (operation.equals("减")) {
            return " - ";
        } else if (operation.equals("乘")) {
            return " * ";
        } else if (operation.equals("除")) {
            return " / ";
        }else {
            log.warn("other operation: {}",operation);
            return operation;
        }
    }

    private String getCompare(String compare) {
        if (compare.equals("小于")) {
            return " < ";
        } else if (compare.equals("等于")) {
            return " == ";
        } else if (compare.equals("大于")) {
            return " > ";
        } else if (compare.equals("小于等于")) {
            return " <= ";
        } else if (compare.equals("大于等于")) {
            return " >= ";
        }else {
            log.warn("other operation: {}",compare);
            return String.format(" %s ", compare);
        }
    }
}
