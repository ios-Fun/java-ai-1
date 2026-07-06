package com.changgeng.model;

import lombok.Data;

import java.util.Date;

@Data
public class DefectIncidentInfo {
    Integer dataId;
    String name;
    Double severity;
    Double maxSeverity;
    Double realSeverity;
    Date lastTime;
    String type;
    Long nodeId;
    String level;
    Integer incidentId;
    Date maxTime;
    String unit;
    Double value;
    Long subsystemId;
    String tagCode;
    String status;
    String isRed;
    String alarmType;
    Integer closed;

}
