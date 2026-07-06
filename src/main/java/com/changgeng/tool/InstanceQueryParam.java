package com.changgeng.tool;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author henry
 * @Date 2022/1/14 17:40
 * @Version 1.0
 */
@Data
@NoArgsConstructor
public class InstanceQueryParam {

    private Long nodeId;

    private Long parentNodeId;

    private List<String> labels;

    private Map<String, Object> properties;

    private List<String> propertiesKeys;

    private Integer page;

    private Integer pageSize;

    private String childPropertyKey;

    private Map<String,String> childPropertyFuzzyQueryMap;

    private Map<String,Object> childPropertyQueryMap;

    private Map<String, String> propertyFuzzyQueryMap;

    public InstanceQueryParam(List<String> labels) {
        this.labels = labels;
    }

    public InstanceQueryParam(Long nodeId, List<String> propertiesKeys) {
        this.nodeId = nodeId;
        this.propertiesKeys = propertiesKeys;
    }

    public InstanceQueryParam(Long nodeId, List<String> labels, List<String> propertiesKeys) {
        this.nodeId = nodeId;
        this.labels = labels;
        this.propertiesKeys = propertiesKeys;
    }

    public InstanceQueryParam(Long nodeId, Long parentNodeId, List<String> labels) {
        this.nodeId = nodeId;
        this.parentNodeId = parentNodeId;
        this.labels = labels;
    }

    public InstanceQueryParam(Long nodeId, Long parentNodeId, List<String> labels, List<String> propertiesKeys) {
        this.nodeId = nodeId;
        this.parentNodeId = parentNodeId;
        this.labels = labels;
        this.propertiesKeys = propertiesKeys;
    }

    public List<String> getLabels() {
        if (labels == null) {
            labels = new ArrayList<>();
        }
        return labels;
    }

    public void setLabels(List<String> labels) {
        this.labels = labels;
    }

    public void addLabel(String label){
        if(labels == null){
            labels = new ArrayList<>();
        }
        labels.add(label);
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    public Map<String, Object> getProperties() {
        if (properties == null) {
            properties = new HashMap<>();
        }
        return properties;
    }

    public List<String> getPropertiesKeys() {
        if (propertiesKeys == null) {
            propertiesKeys = new ArrayList<>();
        }
        return propertiesKeys;
    }

    public void setPropertiesKeys(List<String> propertiesKeys) {
        this.propertiesKeys = propertiesKeys;
    }

    public void addPropertiesKey(String propertyKey){
        if(this.propertiesKeys == null){
            this.propertiesKeys = new ArrayList<>();
        }
        propertiesKeys.add(propertyKey);
    }

}
