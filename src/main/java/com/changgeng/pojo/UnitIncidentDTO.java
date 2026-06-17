package com.changgeng.pojo;

import com.changgeng.model.DefectIncidentInfo;
import lombok.Data;

import java.util.List;

@Data
public class UnitIncidentDTO {
    private Integer unitId;
    private String unitName;
    private List<DefectIncidentInfo> incidents;
}
