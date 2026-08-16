package com.changgeng.service;


import com.alibaba.excel.util.StringUtils;
import com.changgeng.client.DamExtClient;
import com.changgeng.common.result.Result;
import com.changgeng.controller.CommonController;
import com.changgeng.handler.InfluxDBServiceJR;
import com.changgeng.mapper.IndicatorEgulationsMapper;
import com.changgeng.pojo.IndicatorEgulations;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TagService {

    @Autowired
    DamExtClient damExtClient;

    @Autowired
    InfluxDBServiceJR influxDBServiceJR;

    @Autowired
    CommonController commonController;

    @Autowired
    private IndicatorEgulationsMapper indicatorEgulationsMapper;

    /** 统计采样间隔(秒)，1分钟一个点 */
    private static final int STATISTIC_INTERVAL = 60;

    /**
     * 统计结果列定义
     * sys/sub/tag/unit: 系统/子系统/测点名/单位
     * min/max/avg:      实际值最小值/最大值/平均值
     * emin/emax/eavg:   估计值最小值/最大值/平均值(最小、最大取实际值极值时刻对应的估计值)
     * smin/smax/savg:   严重度最小值/最大值/平均值(最小、最大取实际值极值时刻对应的严重度)
     * low/normal/high:  超限偏低/正常/偏高个数
     */
    private static final List<String> STATISTIC_COLS = Arrays.asList(
            "systemName", "subsystemName", "tagName", "unit",
            "realTimeDateMin", "realTimeDateMax", "realTimeDateAvg",
            "estimateMin", "estimateMax", "estimateAvg",
            "severityMin", "severityMmax", "severityAvg",
            "XXLowNum", "normalNum", "XXHignum"
            );

    public List<Map> getTagInfos(
            Integer tagId,
            String tagName,
            String srcTagName,
            String name
    ) {
        List<Map> list;
        // 模糊name查询
        if (name != null && !name.isEmpty()) {
            list = damExtClient.getTagInfosByName(name);
        } else {
            list = damExtClient.getTagInfosByTTS(tagId, tagName, srcTagName);
        }
        return list;
    }

    /**
     * 测点统计数据
     * 三个测点参数都不传时统计所有测点，否则只统计命中的测点。
     * 统计区间为 [startTime, endTime]，startTime 为空时默认取 endTime 往前半小时。
     *
     * @return {"cols": [...列名...], "data": [[...每个测点一行...]]}，无数据时返回空 Map
     */
    public Map<String, Object> tagStatisticData(Integer tagId, String tagName, String srcTagName, String startTime, String endTime, String parentName) {
        List<Map> tagInfos;
        if (tagId == null && tagName == null && srcTagName == null) {
            List<Map> parentNameMarched = (List<Map>) commonController.matchForBest(parentName, 1).getData();
            tagInfos = damExtClient.getAllTags(parentNameMarched.get(0).get("type").toString(), parentNameMarched.get(0).get("name").toString());
        } else {
            tagInfos = getTagInfos(tagId, tagName, srcTagName, null).stream()
                    .map(o ->{
                        Map tagInfo= new HashMap<>();
                        tagInfo.put("name", o.get("名称"));
                        tagInfo.put("tagCode", o.get("编码"));
                        tagInfo.put("srcTagName", o.get("源标签点名"));
                        tagInfo.put("srcTagDesc", o.get("源标签点描述"));
                        tagInfo.put("unit", o.get("单位"));
                        tagInfo.put("systemId", o.get("systemId"));
                        tagInfo.put("systemName", o.get("systemName"));
                        tagInfo.put("subSystemId", o.get("subSystemId"));
                        tagInfo.put("subSystemName", o.get("subSystemName"));
                        return tagInfo;
                    }).collect(Collectors.toList());
        }
        if (tagInfos == null || tagInfos.isEmpty()) return new LinkedHashMap<>();

        // 开始时间缺省: 结束时间往前半小时
        String beginTime = (startTime == null || startTime.isEmpty())
                ? influxDBServiceJR.beforeXMinutes(endTime)
                : startTime;

        List<List<Object>> data = tagInfos.parallelStream()
                .map(tagInfo -> statisticOneTag(tagInfo, beginTime, endTime))
                .filter(row -> row != null)
                .collect(Collectors.toList());
        if (data.isEmpty()) {
            return new LinkedHashMap<>();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cols", STATISTIC_COLS);
        result.put("data", data);
        return result;
    }

    /**
     * 单个测点的统计，返回和 STATISTIC_COLS 顺序一致的一行数据，无实际值数据时返回 null
     */
    private List<Object> statisticOneTag(Map tagInfo, String beginTime, String endTime) {
        String tagCode = tagInfo.get("tagCode").toString();
        Integer subsystemId = (Integer) tagInfo.get("subSystemId");

        // 实际值、估计值、严重度，1分钟一个点
        List<Map> realValues = influxDBServiceJR.queryValues3(tagCode, subsystemId, beginTime, endTime, "RealTimeData", STATISTIC_INTERVAL);
        Stat realStat = stat(realValues);
        if (realStat == null) {
            log.warn("测点 {} 在 {} ~ {} 无实际值数据", tagCode, beginTime, endTime);
            return null;
        }
        List<Map> estimateValues = influxDBServiceJR.queryValues3(tagCode, subsystemId, beginTime, endTime, "Estimate", STATISTIC_INTERVAL);
        List<Map> severityValues = influxDBServiceJR.queryValues3(tagCode, subsystemId, beginTime, endTime, "TagSeverity", STATISTIC_INTERVAL);
        List<Map> XXValues = influxDBServiceJR.queryValues3(tagCode, subsystemId, beginTime, endTime, "XX", STATISTIC_INTERVAL);

        Stat estimateStat = stat(estimateValues);
        Stat severityStat = stat(severityValues);

        // 超限个数
        int[] limitCounts = XXValues.stream()
                .filter(m -> m.get("value") != null)
                .mapToDouble(m -> (double) m.get("value"))
                .collect(
                        () -> new int[]{0, 0, 0},
                        (a, v) -> { if (v < 0) a[0]++; else if (v == 0) a[1]++; else a[2]++; },
                        (a, b) -> { a[0] += b[0]; a[1] += b[1]; a[2] += b[2]; }
                );


        List<Object> row = new ArrayList<>(STATISTIC_COLS.size());
        row.add(tagInfo.get("systemName"));
        row.add(tagInfo.get("subSystemName"));
        row.add(tagInfo.get("name"));
        row.add(tagInfo.get("unit"));

        row.add(Math.round(realStat.min * 1000) / 1000.0);
        row.add(Math.round(realStat.max * 1000) / 1000.0);
        row.add(Math.round(realStat.avg * 1000) / 1000.0);

        row.add(estimateStat==null?null:Math.round(estimateStat.min * 1000) / 1000.0);
        row.add(estimateStat==null?null:Math.round(estimateStat.max * 1000) / 1000.0);
        row.add(estimateStat==null?null:Math.round(estimateStat.avg * 1000) / 1000.0);

        row.add(severityStat==null?null:Math.round(severityStat.min * 1000) / 1000.0);
        row.add(severityStat==null?null:Math.round(severityStat.max * 1000) / 1000.0);
        row.add(severityStat==null?null:Math.round(severityStat.avg * 1000) / 1000.0);

        row.add(limitCounts[0]);
        row.add(limitCounts[1]);
        row.add(limitCounts[2]);

        return row;
    }

    /**
     * 计算一组时序数据的最小值、最大值、平均值，并记录最小、最大值出现的时间
     * 空值和 valid 为 false 的点不参与统计，无有效点时返回 null
     */
    private Stat stat(List<Map> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        Stat stat = new Stat();
        double sum = 0.0;
        int count = 0;
        for (Map item : values) {
            Object valObj = item.get("value");
            if (!(valObj instanceof Number)) {
                continue;
            }
            // 无效点不参与统计
            if (Boolean.FALSE.equals(item.get("valid"))) {
                continue;
            }
            double value = ((Number) valObj).doubleValue();
            String time = (String) item.get("time");
            if (count == 0 || value < stat.min) {
                stat.min = value;
                stat.minTime = time;
            }
            if (count == 0 || value > stat.max) {
                stat.max = value;
                stat.maxTime = time;
            }
            sum += value;
            count++;
        }
        if (count == 0) {
            return null;
        }
        stat.avg = sum / count;
        return stat;
    }

    /**
     * 时序数据转 时间 -> 值 的映射，用于按时刻取对应的估计值、严重度
     */
    private Map<String, Double> toTimeValueMap(List<Map> values) {
        Map<String, Double> map = new HashMap<>();
        if (values == null) {
            return map;
        }
        for (Map item : values) {
            Object valObj = item.get("value");
            Object timeObj = item.get("time");
            if (timeObj == null || !(valObj instanceof Number)) {
                continue;
            }
            map.put((String) timeObj, ((Number) valObj).doubleValue());
        }
        return map;
    }

    public List<Map> getAllTags(String type, String parentName) {
        return damExtClient.getAllTags(type, parentName);
    }

    /**
     * 按 tagType 分类查询并组装环保指标
     */
    public Result selectEnvironmentalExamplesByFuzzyMatching(String fuzzyName, Integer tagId, String tagName) {
        // 1. 获取原始数据
        List<Map> rawData = damExtClient.selectEnvironmentalExamplesByFuzzyMatching(fuzzyName, tagId, tagName);
        if (rawData == null || rawData.isEmpty()) {
            return Result.success("统计查询失败，无对应数据");
        }

        // 2. 获取所有标准限值
        List<IndicatorEgulations> allLimits = indicatorEgulationsMapper.getAllIndicatorEgulations();

        // 3. 核心改造：按 tagType 分组，并在分组过程中移除该字段
        Map<String, List<Map>> groupedResult = rawData.stream()
                .collect(Collectors.groupingBy(
                        // Key: 使用 tagType 作为分组的键
                        item -> String.valueOf(item.get("tagType")),

                        // Value: 对分组内的列表进行处理
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                list -> {
                                    // 遍历分组后的列表，执行两件事：
                                    // 1. 移除内部的 tagType 字段
                                    // 2. 组装 IndicatorEgulations 指标数据
                                    list.forEach(map -> {
                                        // 【关键步骤】移除作为 Key 的 tagType，避免 JSON 冗余
                                        map.remove("tagType");

                                        // 组装关联的环保指标
                                        List<Map> matchedItems = allLimits.stream()
                                                .filter(limit -> isMatch(String.valueOf(map.get("name")), limit.getName()))
                                                .map(limit -> {
                                                    Map<String, String> item = new HashMap<>();
                                                    item.put("limit", limit.getLimit());
                                                    item.put("name", limit.getName());
                                                    item.put("newLimit", limit.getNewLimit());
                                                    item.put("importantLimit", limit.getImportantLimit());
                                                    return item;
                                                })
                                                .collect(Collectors.toList());

                                        map.put("IndicatorEgulations", matchedItems);
                                    });
                                    return list;
                                }
                        )
                ));

        return Result.success(groupedResult);
    }

    /**
     * 抽取出来的匹配逻辑，保持主流程清晰
     */
    private List<Map> matchIndicators(Map tag, List<IndicatorEgulations> allLimits) {
        String tagName = tag.get("name") != null ? tag.get("name").toString() : "";

        return allLimits.stream()
                .filter(limit -> isMatch(tagName, limit.getName())) // 调用你原本的自定义匹配方法
                .map(limit -> {
                    Map<String, String> item = new HashMap<>();
                    item.put("limit", limit.getLimit());
                    item.put("name", limit.getName());
                    item.put("newLimit", limit.getNewLimit());
                    item.put("importantLimit", limit.getImportantLimit());
                    return item;
                })
                .collect(Collectors.toList());
    }

    // 简单的匹配算法示例
    private boolean isMatch(String tagName, String standardName) {
        if (StringUtils.isBlank(tagName) || StringUtils.isBlank(standardName)) return false;
        // 逻辑：如果测点名包含标准名 (如 "烟气温度" 包含 "温度")
        // 或者 标准名包含测点名
        return tagName.contains(standardName) || standardName.contains(tagName);
    }

    /** 时序统计中间结果 */
    private static class Stat {
        Double min;
        Double max;
        Double avg;
        String minTime;
        String maxTime;
    }
}
