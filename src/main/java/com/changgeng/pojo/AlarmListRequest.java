package com.changgeng.pojo;

import lombok.Data;

import java.util.Date;

@Data
public class AlarmListRequest {
    private String tagName;
    private String tagSourceName;
    private Date startTime;
    private Date endTime;
    private Integer assetNumber;
    private String dataType;
    private String currentStatusName;
    private Integer tagId;
    private Integer monitorPointId;
    private Integer unitId;
    private Boolean closed=false;
    private Boolean AI=true;
}
