package com.changgeng.mapper;

import com.changgeng.model.DefectIncidentInfo;
import org.apache.ibatis.annotations.Param;

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

    // 获取
    List<DefectIncidentInfo> selectDefectIncidentInfoByIncidentId(Integer incidentId);

    // 根据诊断单获取（故障模式，特征，测点）信息
    List<DefectIncidentInfo> selectDefectIncidentListById(Integer defectId);

    // 获取所有未关闭诊断单内设备与其对应id
    List<DefectIncidentInfo> selectOpenedIncidentAssetName();

    // 根据设备id获取对应未关闭诊断单内所有测点信息
    List<DefectIncidentInfo> selectOpenedIncidentAssetTagsByAssetId(@Param("assetId") Long assetId);

    // 获取所有未关闭诊断单内设备名称
    List<String> getUnClosedIncidentAssetNames();

    // 获取未关闭的单子下，包含指定设备的测点
    List<DefectIncidentInfo> getUnClosedIncidentByAssetName(@Param("assetName") String assetName);
}
