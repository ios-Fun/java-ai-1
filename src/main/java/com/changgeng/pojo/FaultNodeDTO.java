package com.changgeng.pojo;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class FaultNodeDTO {
    private Long id;
    private String name;
    private List<String> label;
    private Double x, y;
    private Long sourceId;
    private String weight;
    private String status;
    private Map<String, Object> properties;
    private List<ParentLink> parentIds;

    @Data
    public static class ParentLink {
        private Long id;       // 注意：这里实际指向的是当前节点自身或父节点，需根据业务确认
        private String weight;
        private String status;
    }
}
