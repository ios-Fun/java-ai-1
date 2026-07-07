package com.changgeng.service;

import com.changgeng.client.DamExtClient;
import com.changgeng.mapper.AlarmTableMapper;
import com.changgeng.mapper.DefectIncidentMapper;
import com.changgeng.pojo.AlarmListRequest;
import com.changgeng.pojo.AlarmTable;
import com.changgeng.pojo.SystemIncidentInfo;
import com.changgeng.pojo.SystemIncidentRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class UnitService {

    @Autowired
    DamExtClient damExtClient;
    @Autowired
    AlarmTableMapper alarmTableMapper;
    @Autowired
    DefectIncidentMapper defectIncidentMapper;

    public List<Map> getItems(Integer unitId, String type) {
        List<Map> list = damExtClient.getItems(unitId, type);
        return list;
    }

    public List<AlarmTable> getAlarmList(AlarmListRequest request) {
        List<AlarmTable> alarmList = alarmTableMapper.selectAlarmList(request);
        return alarmList;
    }

    public List<SystemIncidentInfo> getSystemIncidentList(SystemIncidentRequest request) {
        List<SystemIncidentInfo> list = defectIncidentMapper.selectSystemIncidentList(request);
        return list;
    }

    public List<SystemIncidentInfo> getSubSystemIncidentList(SystemIncidentRequest request) {
        List<SystemIncidentInfo> list = defectIncidentMapper.selectSubSystemIncidentList(request);
        return list;
    }
}
