package com.changgeng.controller;


import com.alibaba.excel.util.StringUtils;
import com.changgeng.client.DamExtClient;
import com.changgeng.common.result.Result;
import com.changgeng.handler.InfluxDBServiceJR;
import com.changgeng.mapper.IndicatorEgulationsMapper;
import com.changgeng.pojo.IndicatorEgulations;
import com.changgeng.service.TagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/ai/tag")
@Slf4j
public class TagController {

    @Autowired
    DamExtClient damExtClient;

    @Autowired
    InfluxDBServiceJR influxDBServiceJR;
    @Autowired
    TagService tagService;


    /**
     * 测点信息查询接口
     * 支持两种方式:
     * 1. 精确查询: 通过 tagId(测点ID)、tagName(测点编码)、srcTagName(源标签点名) 三者之一进行精确匹配
     * 2. 模糊查询: 通过 tagName(测点名称) 进行模糊匹配
     *
     * 请求参数说明:
     * @param tagId: 测点ID(可选),精确匹配
     * @param tagName: 测点编码(可选),精确匹配
     * @param srcTagName: 源标签点名(可选),精确匹配
     * @param name: 测点名称(可选),模糊匹配
     * @return 测点信息列表,包含测点ID、编码、源标签点名、名称等信息
     */
    @RequestMapping("/getTagInfos")
    public Result setTagInfos(@RequestParam(required = false) Integer tagId, @RequestParam(required = false) String tagName, @RequestParam(required = false) String srcTagName,
                              @RequestParam(required = false) String name
    ) {
        log.info("查询测点信息 - tagId: {}, tagName: {}, srcTagName: {}, tagName: {}", tagId, tagName, srcTagName, name);

        if (tagId == null && tagName == null && srcTagName == null && name == null) {
            return Result.error(400, "至少输入1个参数");
        }
        List<Map> result = tagService.getTagInfos(tagId, tagName, srcTagName, name);
        return result.isEmpty() ? Result.success("未查到测点信息") : Result.success(result);
    }

    /**
     * 测点挂载路径查询接口
     * 通过测点ID、编码或源标签点名精确查找测点的挂载路径。
     * 一个测点可能挂载在多处,因此会返回多条路径信息。
     *
     * 请求参数说明:
     * @param tagId 测点ID
     * @param tagName 测点编码
     * @param srcTagName 源标签点名
     * @return 测点挂载路径列表,每条路径包含完整的层级关系(如: 机组->系统->子系统->设备->测点)
     */
    @RequestMapping("/getTagPaths")
    public Result getTagPaths(@RequestParam(required = false) Integer tagId, @RequestParam(required = false) String tagName, @RequestParam(required = false) String srcTagName
    ) {
        log.info("查询测点路径 - tagId: {}, tagName: {}, srcTagName: {}", tagId, tagName, srcTagName);
        if (tagId == null && tagName == null && srcTagName == null) {
            return Result.error(400, "至少输入1个参数");
        }
        List<Map> result = damExtClient.getTagPathsByTTS(tagId, tagName, srcTagName);
        return result.isEmpty() ? Result.success("未查到路径信息") : Result.success(result);
    }

    /**
     * 测点历史数据查询接口
     * 通过测点ID、编码或源标签点名精确查找指定时间段内的测点数据,类型包括:
     * - 实际值(RealTimeData)
     * - 估计值(Estimate)
     * - 严重度(TagSeverity)
     * - 所有(All)
     * 如果不传时间参数,默认查询最近6小时到现在的数据。
     *
     * 请求参数说明:
     * @param tagId 测点ID
     * @param tagName 测点编码
     * @param srcTagName 源标签点名
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @param type 查询类型(可选),默认为"all"
     * @return 测点历史数据列表,包含时间戳、实际值、估计值、严重度等信息
     */
    @RequestMapping("/tagValues")
    public Result getTagValues(
            @RequestParam(required = false) Integer tagId,
            @RequestParam(required = false) String tagName,
            @RequestParam(required = false) String srcTagName,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = true) String type,
            @RequestParam(required = false) Integer interval
    ) {
        log.info("查询测点历史数据 - tagId: {}, tagName: {}, srcTagName: {}, startTime: {}, endTime: {}, type: {}, interval: {}",
                tagId, tagName, srcTagName, startTime, endTime, type, interval);
        if (tagId == null && tagName == null && srcTagName == null) {
            return Result.error(400, "至少输入1个参数");
        }
        // 处理时间参数 没有就查询最近6小时
        if (startTime==null && endTime==null &&  startTime.isEmpty() && endTime.isEmpty() ) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
                    .withZone(ZoneId.of("UTC"));
            Date now = new Date();
            endTime = formatter.format(now.toInstant());
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(now);
            calendar.add(Calendar.HOUR, -6);
            startTime = formatter.format(calendar.getTime().toInstant());
        }

        Integer subsystemId = damExtClient.getSubSystemIdByTTS(tagId, tagName, srcTagName);
        if (tagName==null || tagName.isEmpty()) {
            List<Map> tagInfos = damExtClient.getTagInfosByTTS(tagId, tagName, srcTagName);
            tagName = tagInfos.get(0).get("编码").toString();
        }
        Map result = new HashMap();
        if(type.equals("all")) {
            List<Map> actualValues = influxDBServiceJR.queryValues3(tagName, subsystemId, startTime, endTime, "RealTimeData",interval);
            List<Map> estimatedValues = influxDBServiceJR.queryValues3(tagName, subsystemId, startTime, endTime, "Estimate",interval);
            List<Map> severityValues = influxDBServiceJR.queryValues3(tagName, subsystemId, startTime, endTime, "TagSeverity",interval);
            List<String> timeList = actualValues.stream().map(item -> (String)item.get("time")).collect(Collectors.toList());
            List<Double> realTimeDataList = actualValues.stream().map(item -> (Double)item.get("value")).collect(Collectors.toList());
            List<Double> estimateList = estimatedValues.stream().map(item -> (Double)item.get("value")).collect(Collectors.toList());
            List<Double> tagSeverityList = severityValues.stream().map(item -> (Double)item.get("value")).collect(Collectors.toList());
                    
            result.put("time", timeList);
            result.put("RealTimeData", realTimeDataList);
            result.put("Estimate", estimateList);
            result.put("TagSeverity", tagSeverityList);
        }else {
            List<Map> values = influxDBServiceJR.queryValues3(tagName, subsystemId, startTime, endTime, type,interval);
            List<String> timeList = values.stream().map(item -> (String)item.get("time")).collect(Collectors.toList());
            List<Double> valuesList = values.stream().map(item -> (Double)item.get("value")).collect(Collectors.toList());

            result.put("time", timeList);
            result.put(type, valuesList);
        }
                
        return Result.success(result);
    }

    /**
     * 测点统计数据查询接口
     * 统计指定时间段内测点的实际值、估计值、严重度的最小值/最大值/平均值以及超限个数，
     * 数据按1分钟间隔采样。三个测点参数都不传时统计所有测点。
     * 请求参数说明:
     * @param tagId      测点ID(可选)
     * @param tagName     测点编码(可选)
     * @param srcTagName 源标签点名(可选)
     * @param startTime  开始时间(可选),不传默认取结束时间往前半小时
     * @param endTime    结束时间(必填)
     * @return cols 为列名列表, data 为每个测点一行的统计数据
     */
    @RequestMapping("/tagStatisticData")
    public Result getTagStatisticData(
            @RequestParam(required = false) Integer tagId,
            @RequestParam(required = false) String tagName,
            @RequestParam(required = false) String srcTagName,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = true) String endTime,
            @RequestParam(required = false) String parentName
    ) {
        log.info("查询测点统计数据 - tagId: {}, tagName: {}, srcTagName: {}, startTime: {}, endTime: {}, parentName: {}",
                tagId, tagName, srcTagName, startTime, endTime, parentName);
        if (endTime == null || endTime.isEmpty()) {
            return Result.error(400, "时间传递错误");
        }
        Map<String, Object> result = tagService.tagStatisticData(tagId, tagName, srcTagName, startTime, endTime, parentName);
        return result.isEmpty() ? Result.success("统计数据失败") : Result.success(result);
    }

    @RequestMapping("/getAllTags")
    public Result getAllTags(
            @RequestParam(required = true) String type,
            @RequestParam(required = false) String parentName
    ) {
        log.info("查询所有标签 - type: {}, parentName: {}", type, parentName);
        List<Map> result = tagService.getAllTags(type, parentName);
        return result.isEmpty() ? Result.success("查询失败") : Result.success(result);
    }

    /*
     * 测点单时刻历史数据查询
     * 通过测点ID、编码或源标签点名精确查找指定时间段内的测点数据,类型包括:
     * - 实际值(RealTimeData)
     * - 估计值(Estimate)
     * - 严重度(TagSeverity)
     * - 所有(All)
     * 如果不传时间参数,默认查询最近6小时到现在的数据。
     *
     * 请求参数说明:
     * @param tagId 测点ID
     * @param tagName 测点编码
     * @param srcTagName 源标签点名
     * @param time 时间
     * @param type 查询类型(可选),默认为"all"
     * @return 测点历史数据列表,包含时间戳、实际值、估计值、严重度等信息
     */
    @RequestMapping("/tagValue")
    public Result getTagValue(
            @RequestParam(required = false) Integer tagId,
            @RequestParam(required = false) String tagName,
            @RequestParam(required = false) String srcTagName,
            @RequestParam(required = false) String time,
            @RequestParam(required = false) String type
    ) {
        log.info("查询测点历史数据 - tagId: {}, tagName: {}, srcTagName: {}, time: {}, type: {}",
                tagId, tagName, srcTagName, time, type);
        if (tagId == null && tagName == null && srcTagName == null) {
            return Result.error(400, "至少输入1个参数");
        }


        Integer subsystemId = damExtClient.getSubSystemIdByTTS(tagId, tagName, srcTagName);
        if (tagName==null || tagName.isEmpty()) {
            List<Map> tagInfos = damExtClient.getTagInfosByTTS(tagId, tagName, srcTagName);
            tagName = tagInfos.get(0).get("编码").toString();
        }
        // Map result = new HashMap();
        StringBuilder stringBuilder = new StringBuilder();

        // 处理时间参数
        if (time==null || time.isEmpty()) {
            if(type.equals("all")) {
                String actualValues = influxDBServiceJR.queryLatest(tagName, subsystemId, "RealTimeData");
                String estimatedValues = influxDBServiceJR.queryLatest(tagName, subsystemId, "Estimate");
                String severityValues = influxDBServiceJR.queryLatest(tagName, subsystemId, "TagSeverity");
                stringBuilder.append(actualValues);
                stringBuilder.append("\n");
                stringBuilder.append(estimatedValues);
                stringBuilder.append("\n");
                stringBuilder.append(severityValues);
                stringBuilder.append("\n");
            }else {
                String values = influxDBServiceJR.queryLatest(tagName, subsystemId, type);
                stringBuilder.append(values);
                stringBuilder.append("\n");
            }
        } else {
            if(type.equals("all")) {
                String actualValues = influxDBServiceJR.queryValueAtTime(tagName, subsystemId, "RealTimeData", time);
                String estimatedValues = influxDBServiceJR.queryValueAtTime(tagName, subsystemId, "Estimate", time);
                String severityValues = influxDBServiceJR.queryValueAtTime(tagName, subsystemId, "TagSeverity", time);
                stringBuilder.append(actualValues);
                stringBuilder.append("\n");
                stringBuilder.append(estimatedValues);
                stringBuilder.append("\n");
                stringBuilder.append(severityValues);
                stringBuilder.append("\n");
            }else {
                String values = influxDBServiceJR.queryValueAtTime(tagName, subsystemId, type, time);
                stringBuilder.append(values);
                stringBuilder.append("\n");
            }
        }
        log.info("getTagValue result: {}", stringBuilder.toString());
        return Result.success(stringBuilder.toString());
    }


    /**
     * 环保测点，指标查询接口
     * 根据传入tagId，tagName精准查询指定环保测点或环保指标，或根据fuzzyName模糊查询指定环保测点或环保指标
     * 请求参数说明:
     * @param tagId      测点ID(可选)
     * @param tagName     测点编码(可选)
     * @param fuzzyName 模糊匹配名称(可选)
     * @return cols 为列名列表, data 为每个测点一行的统计数据
     */
    @RequestMapping("/selectEnvironmentalExamplesByFuzzyMatching")
    public Result selectEnvironmentalExamplesByFuzzyMatching(
            @RequestParam(required = false) Integer tagId,
            @RequestParam(required = false) String tagName,
            @RequestParam(required = false) String fuzzyName
    ) {
        log.info("查询测点统计数据 - tagId: {}, tagName: {}, fuzzyName: {}",
                tagId, tagName, fuzzyName);
        return tagService.selectEnvironmentalExamplesByFuzzyMatching(fuzzyName, tagId, tagName);

    }


}
