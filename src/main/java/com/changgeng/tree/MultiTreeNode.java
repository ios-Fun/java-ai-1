package com.changgeng.tree;

import lombok.Data;

import java.util.List;
import  java.util.Map;

@Data
public class MultiTreeNode {
    // 节点数据
    public TreeNodeValue data;
    public List<MultiTreeNode> children;
}
