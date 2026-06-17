package com.changgeng.tree;

import lombok.Data;

import java.util.Map;

@Data
public class TreeNodeValue {
    public Map value;
    // 0--故障模式，1--特征，2--测点， 3--操作符
    public Integer type;
}
