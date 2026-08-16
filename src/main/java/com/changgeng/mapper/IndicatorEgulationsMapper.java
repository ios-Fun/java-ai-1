package com.changgeng.mapper;

import com.changgeng.pojo.AlarmDefects;
import com.changgeng.pojo.IndicatorEgulations;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface IndicatorEgulationsMapper {
    List<IndicatorEgulations> getAllIndicatorEgulations();
}
