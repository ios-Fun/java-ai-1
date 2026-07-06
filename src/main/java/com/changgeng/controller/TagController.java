package com.changgeng.controller;


import com.changgeng.client.DamExtClient;
import com.changgeng.common.result.Result;
import com.changgeng.handler.InfluxDBServiceJR;
import com.changgeng.service.TagService;
import lombok.extern.slf4j.Slf4j;
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
     * 1. 精确查询: 通过 tagId(测点ID)、tagCode(测点编码)、srcTagName(源标签点名) 三者之一进行精确匹配
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
        log.info("查询测点信息 - tagId: {}, tagCode: {}, srcTagName: {}, tagName: {}", tagId, tagName, srcTagName, name);

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
            @RequestParam(required = true) String type
    ) {
        log.info("查询测点历史数据 - tagId: {}, tagCode: {}, srcTagName: {}, startTime: {}, endTime: {}, type: {}",
                tagId, tagName, srcTagName, startTime, endTime,  type);
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
            List<Map> actualValues = influxDBServiceJR.queryValues3(tagName, subsystemId, startTime, endTime, "RealTimeData");
            List<Map> estimatedValues = influxDBServiceJR.queryValues3(tagName, subsystemId, startTime, endTime, "Estimate");
            List<Map> severityValues = influxDBServiceJR.queryValues3(tagName, subsystemId, startTime, endTime, "TagSeverity");
            List<String> timeList = actualValues.stream().map(item -> (String)item.get("time")).collect(Collectors.toList());
            List<Double> realTimeDataList = actualValues.stream().map(item -> (Double)item.get("value")).collect(Collectors.toList());
            List<Double> estimateList = estimatedValues.stream().map(item -> (Double)item.get("value")).collect(Collectors.toList());
            List<Double> tagSeverityList = severityValues.stream().map(item -> (Double)item.get("value")).collect(Collectors.toList());
                    
            result.put("time", timeList);
            result.put("RealTimeData", realTimeDataList);
            result.put("Estimate", estimateList);
            result.put("TagSeverity", tagSeverityList);
        }else {
            List<Map> values = influxDBServiceJR.queryValues3(tagName, subsystemId, startTime, endTime, type);
            List<String> timeList = values.stream().map(item -> (String)item.get("time")).collect(Collectors.toList());
            List<Double> valuesList = values.stream().map(item -> (Double)item.get("value")).collect(Collectors.toList());

            result.put("time", timeList);
            result.put(type, valuesList);
        }
                
        return Result.success(result);
    }
}
