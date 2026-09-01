package com.changgeng.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

public interface ModelingConfigMapper {
    List<Map> getTagsOfModel(@Param("tagName") String tagName);
}
