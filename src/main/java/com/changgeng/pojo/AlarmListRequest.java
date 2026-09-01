package com.changgeng.pojo;

import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class AlarmListRequest {
    private String tagName;
    private String tagSourceName;
    private Date startTime;
    private Date endTime;
    private Integer assetNumber;
    private Integer assetId;
    private String dataType;
    private String currentStatusName;
    private Integer tagId;
    private List<Integer> tagIds;
    private Integer monitorPointId;
    private Integer unitId;
    private Boolean closed;
    private Boolean AI=true;
}
