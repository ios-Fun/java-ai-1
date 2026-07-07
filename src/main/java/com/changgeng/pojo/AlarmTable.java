package com.changgeng.pojo;

import lombok.Data;

import java.util.Date;

@Data
public class AlarmTable {
    private Integer id;
    private String tagName;
    private String tagSourceName;
    private Date firstTouchTime;
    private Date lastTouchTime;
    private Integer assetNumber;
    private String dataType;
    private Long tagId;
    private Long monitorPointId;
    private Integer unitId;
    private Boolean closed;
    private Integer severityLevel;
    private String alarmType;
    private Date alarmUpdateTime;
    private String tagDescription;
    private String currentStatusName;
    private String currentStatusComment;
    private Integer dealState;
    private String alarmStatus;
    private String alarmLevel;
    private Double variation;
    private String actual;
    private String unit;
}
