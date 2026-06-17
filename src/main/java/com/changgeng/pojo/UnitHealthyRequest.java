package com.changgeng.pojo;

import lombok.Data;

@Data
public class UnitHealthyRequest {
    private String unitName;
    private String startTime;
    private String endTime;
    private String num;
    private String timeUnit;
}
