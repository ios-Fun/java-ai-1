package com.changgeng.pojo;


import lombok.Data;

import java.util.List;

@Data
public class PathUnderUnitRequest {
    private Integer unitId;
    List<Integer> incidentIds;
}
