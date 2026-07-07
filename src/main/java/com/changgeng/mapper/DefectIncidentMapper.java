package com.changgeng.mapper;

import com.changgeng.model.DefectIncident;
import com.changgeng.pojo.SystemIncidentInfo;
import com.changgeng.pojo.SystemIncidentRequest;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface DefectIncidentMapper {

    DefectIncident selectDefectIncidentById(Integer defectId);

    List<SystemIncidentInfo> selectSystemIncidentList(@Param("request") SystemIncidentRequest request);

    List<SystemIncidentInfo> selectSubSystemIncidentList(@Param("request") SystemIncidentRequest request);
}
