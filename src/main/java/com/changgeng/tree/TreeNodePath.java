package com.changgeng.tree;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)  // 空 children 不输出，更清爽
public class TreeNodePath {
    private String name;
    private List<TreeNodePath> children;

    public TreeNodePath(String name) {
        this.name = name;
        this.children = new ArrayList<>();
    }

    public String getName() { return name; }
    public List<TreeNodePath> getChildren() { return children; }

    // 查找或添加子节点
    public TreeNodePath addChild(String childName) {
        for (TreeNodePath child : children) {
            if (child.getName().equals(childName)) {
                return child;
            }
        }
        TreeNodePath newNode = new TreeNodePath(childName);
        children.add(newNode);
        return newNode;
    }
}