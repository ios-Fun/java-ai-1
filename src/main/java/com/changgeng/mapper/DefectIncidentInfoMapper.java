package com.changgeng.mapper;

import com.changgeng.model.DefectIncidentInfo;

import java.util.Date;
import java.util.List;

public interface DefectIncidentInfoMapper {
    //  根据设备名获取诊断单信息
    List<DefectIncidentInfo> selectDefectIncidentIdListByName(String deviceName, Date startDate, Date endDate);

    // 根据机组名获取诊断单信息
    List<DefectIncidentInfo> selectDefectIncidentIdListByUnit(Integer unitId, Date startDate, Date endDate, Boolean closed);

    // 根据诊断单获取（故障模式）信息
    List<DefectIncidentInfo> selectDefectIncidentById(Integer defectId);

    // 根据诊断单获取所有故障模式信息
    List<DefectIncidentInfo> selectFaultModeListById(Integer incidentId);

    // 根据诊断单获取（故障模式，特征，测点）信息
    List<DefectIncidentInfo> selectDefectIncidentListById(Integer defectId);
}
