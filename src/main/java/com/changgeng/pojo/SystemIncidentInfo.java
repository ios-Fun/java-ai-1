package com.changgeng.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SystemIncidentInfo {
    private Boolean closed;
    private Integer powerStationId;
    private Integer systemId;
    private Integer subSystemId;
    private Integer unitId;
    private Integer createdUserId;
    private Integer flowNodeId;
    private Integer systemIncidentId;
    private Integer subSystemIncidentId;
    private Double severity;
    private String currentStatus;
    private String handledUserId;
    private String severityLevel;
    private Date lastDealTime;
    private Date triggerTime;
}
