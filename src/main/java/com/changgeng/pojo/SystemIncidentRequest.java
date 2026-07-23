package com.changgeng.pojo;

import lombok.Data;

import java.util.Date;

@Data
public class SystemIncidentRequest {
    private Integer systemId;
    private Integer subSystemId;
    private Integer unitId;
    private Date startTime;
    private Date endTime;
    private String currentStatus;
    private Boolean closed=false;
}
