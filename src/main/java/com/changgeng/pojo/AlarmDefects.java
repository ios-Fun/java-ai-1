package com.changgeng.pojo;

import lombok.Data;

import java.util.Date;

@Data
public class AlarmDefects {
    Date theLastTime;
    Long defectId;
    Double severity;
    Integer type;
    String originalName;
    String defectName;
    Integer alarmIncidentId;
}
