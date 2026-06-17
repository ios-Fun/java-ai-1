package com.changgeng.mapper;

import com.changgeng.pojo.AlarmDefects;

import java.util.List;

public interface AlarmDefectsMapper {
    List<AlarmDefects> getByAlarmIncidentId(Integer alarmIncidentId);
}
