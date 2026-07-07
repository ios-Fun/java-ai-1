package com.changgeng.mapper;

import com.changgeng.pojo.AlarmListRequest;
import com.changgeng.pojo.AlarmTable;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface AlarmTableMapper {

    List<AlarmTable> selectAlarmList(@Param("request") AlarmListRequest request);

}
