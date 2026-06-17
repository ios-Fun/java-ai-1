package com.changgeng.mapper;

import com.changgeng.model.DefectIncidentInfo;

import java.util.Date;
import java.util.List;

public interface DefectIncidentInfoMapper {
    //  根据设备名获取诊断单信息
    List<DefectIncidentInfo> selectDefectIncidentIdListByName(String deviceName, Date startDate, Date endDate);

    // 根据诊断单获取（故障模式）信息
    DefectIncidentInfo selectDefectIncidentById(Integer defectId);

    // 根据诊断单获取（故障模式，特征，测点）信息
    List<DefectIncidentInfo> selectDefectIncidentListById(Integer defectId);
}
