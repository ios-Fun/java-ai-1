package com.changgeng.controller;

import com.changgeng.client.*;
import com.changgeng.config.RagConfig;
import com.changgeng.handler.InfluxDBServiceJR;
import com.changgeng.handler.ReadCSVService;
import com.changgeng.mapper.AlarmDefectsMapper;
import com.changgeng.mapper.DefectIncidentInfoMapper;
import com.changgeng.mapper.DefectIncidentMapper;
import com.changgeng.model.DefectIncident;
import com.changgeng.model.DefectIncidentInfo;
import com.changgeng.pojo.AlarmDefects;
import com.changgeng.pojo.DeviceRequest;
import com.changgeng.pojo.IdObj;
import com.changgeng.pojo.UnitHealthyRequest;
import com.changgeng.pojo.UnitIncidentDTO;
import com.changgeng.service.DeviceHealthyService;
import com.changgeng.tool.DateTool;
import com.changgeng.tree.MultiTreeNode;
import com.changgeng.tree.TreeNodeService;
import com.changgeng.tree.TreeNodeValue;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/ai")
@Slf4j
public class DeviceHealthyController {

    @Autowired
    DefectIncidentInfoMapper defectIncidentInfoMapper;

    @Autowired
    DefectIncidentMapper defectIncidentMapper;

    @Autowired
    AlarmDefectsMapper alarmDefectsMapper;

    @Autowired
    ReadCSVService readCSVService;

    @Autowired
    InfluxDBServiceJR influxDBServiceJR;

    @Autowired
    OllamaClient ollamaClient;

    @Autowired
    DamSdkClient damSdkClient;
    @Autowired
    DamCoreClient damCoreClient;



    @Autowired
    DeviceHealthyService deviceHealthyService;

    @Value("${prompt.file}")
    public String promptPath;

    @Value("${prompt.model}")
    public String promptModel;

    @Autowired
    TreeNodeService treeNodeService;

    String promptStr = null;
    @Autowired
    private DamExtClient damExtClient;

    /**
     * 根据设备名称，匹配是否存在完全相同，或相似的，且正处于报警未确认状态的设备
     * <p>
     * 请求参数说明：
     * - assetName: 设备名称（必填），如 "汽轮机"
     *
     * @param assetName 设备名称
     * @return 当能完全匹配，equals字段为true，返回的第一条likelyAssetNames是匹配设备
     * 否则，返回相似的likelyAssetNames
     */
    @RequestMapping("/abnormal/asset")
    public Map<String, Object> abnormalAsset(@RequestParam String assetName) {
        List<String> likelyAssetNames = deviceHealthyService.getLikelyAssetNames(assetName);
        Boolean equals = false;
        for (String likelyAssetName : likelyAssetNames) {
            if (likelyAssetName.equals(assetName)) {
                equals = true;
                break;
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("equals", equals);
        result.put("likelyAssetNames", likelyAssetNames);
        return result;
    }

    /**
     * 根据设备名称，匹配是否存在完全相同，或相似的，且正处于报警未确认状态的设备
     * <p>
     * 请求参数说明：
     * - assetName: 设备名称（必填），如 "汽轮机"
     * - duration:  时序追溯事件（非必填），时间仅能选择1h，8h，1d，指代1小时，8小时，1天，可缺省，默认为1h
     *
     * @param assetName 设备名称
     * @param duration
     * @return 返回匹配到的设备的测点信息，包括时序数据和基础的测点属性
     */
    @RequestMapping("/abnormal/asset/incident")
    public Map<String, Map> abnormalAssetIncident(@RequestParam String assetName, @RequestParam(required = false) String duration) {
        return deviceHealthyService.getUnClosedIncidentTagsByAssetName(assetName, duration);
    }


    /**
     * 设备测点关系接口
     * 根据设备名称信息信息，返回机组名称，设备名称，特征名称，测点描述数据
     * <p>
     * 请求参数说明：
     * - assetName: 设备名称（必填），如 "汽轮机"
     *
     * @param assetName 设备名称
     * @return 机组名称，设备名称，特征名称，测点描述数据
     */
    @RequestMapping("/asset/tag")
    public String assetTag(@RequestParam String assetName) {
        List<Map<String, Object>> assets = damExtClient.getUnitsOrAssetsProps(assetName, null);
        return getTableByList(assets, assetName);
    }


    /**
     * rag检索结果
     * @param tagName 测点名
     * @return
     */
    @RequestMapping("/device/rag")
    public String deviceRag(@RequestParam String tagName) {
        return deviceHealthyService.deviceRag(tagName);
    }

    /**
     * rag查找
     * @param cached_defectIds 诊断单list
     * @return
     */
    @RequestMapping("/device/rag/v2")
    public String deviceRagV2(@RequestBody List<Object> cached_defectIds) {
        // return deviceHealthyService.deviceRag(tagName);
        StringBuilder tagsNamesString = new StringBuilder();
        Set<Long> defectModeIdSets = new HashSet<>();
        for (int j = 0; j < cached_defectIds.size(); j++) {
            Object item = cached_defectIds.get(j);
            Integer incidentId = null;
            if (item instanceof Map) {
                Map<String, Object> dataMap = (Map<String, Object>) item;
                incidentId = (Integer) dataMap.get("incidentId");
            }else if (item instanceof Integer){
                incidentId = (Integer) item;
            }else if (item instanceof String){
                incidentId = Integer.valueOf(String.valueOf(item));
            }
            // 获取故障模式--有可能故障模式不一样
            List<DefectIncidentInfo> defectModeIncidentInfoList = defectIncidentInfoMapper.selectDefectIncidentById(incidentId);
            for (DefectIncidentInfo defectModeIncidentInfo: defectModeIncidentInfoList) {
                if (defectModeIdSets.contains(defectModeIncidentInfo.getNodeId())) {
                    continue;
                }
                defectModeIdSets.add(defectModeIncidentInfo.getNodeId());
                // 故障模式的层级和RAG
                Integer defectModeId = Math.toIntExact(defectModeIncidentInfo.getNodeId());
                // 层级和测点
                String[] results = deviceHealthyService.deviceGraphShow(defectModeId);
                tagsNamesString.append(results[1]).append(",");
            }
        }
        return deviceHealthyService.deviceRag(tagsNamesString.toString());
    }

    /**
     * 未关闭报警单内设备下测点时间戳，实际值，估计值，严重度
     * 统计时间为最后触发时间前8小时，默认数据间隔1分钟
     * 请求参数说明：
     * - assetName: 设备名称
     * @return 以测点编码分组的实际值，估计值，严重度
     */
    @RequestMapping("/device/incidentTagsTrend")
    public Map incidentTagsTrend(@RequestParam String assetName) {
        return deviceHealthyService.incidentTagsTrend(assetName);
    }

    /**
     * 未关闭报警单内设备下测点特征
     * 请求参数说明：
     * - assetName: 设备名称
     * @return 以测点编码分组的实际值，估计值，严重度
     */
    @RequestMapping("/device/incidentTagsAttr")
    public Map incidentTagsAttr(@RequestParam String assetName) {
        return deviceHealthyService.incidentTagsAttr(assetName);
    }

    /**
     * 测点的趋势图信息，应该是多个测点，对应不同的时间，包含了实际值，估计值
     * @param cached_TagsTrendPara 上一个mcp方法缓存的
     * @return
     */
    @RequestMapping("/device/tagsTrend")
    public String tagsTrend(@RequestBody List<Object> cached_TagsTrendPara) {
        log.info("tagsTrend: {}", cached_TagsTrendPara);

        // 从开始时间到结束时间，间隔一分钟
//        pointsSB.append(String.format("\n从 %s 到 %s ", beginTimeStr, endTimeStr));
//        String deviceNameStr = null;
//        String deviceTypeStr = null;
        StringBuilder pointsSB = new StringBuilder();
        for (Object item : cached_TagsTrendPara) {
            Map itemMap = (Map) item;
            log.info("itemMap: {}", itemMap);
            if (!(itemMap.containsKey("subsystemId") && itemMap.containsKey("tagCode"))){
                return "参数错误";
            }
            Integer subsystemId = (Integer) itemMap.get("subsystemId");
            String tagCode = (String) itemMap.get("tagCode");
            String beginTime = (String) itemMap.get("beginTime");
            String endTime = (String) itemMap.get("endTime");

            // String type = defectIncidentInfo.getType();
//                log.info("tag: {}, {}, {}", defectIncidentInfo.getTagCode(), defectIncidentInfo.getSubsystemId(), defectIncidentInfo.getName());
                // 查询测点数据
                List<Double> pointValues = influxDBServiceJR.queryValues(tagCode, Long.valueOf(subsystemId), beginTime, endTime);
                pointsSB.append("\n");
                pointsSB.append("测点编码：");
                pointsSB.append(tagCode);
                pointsSB.append(", 测点值：[");
                if (pointValues.size() == 0) {
                    log.warn("测点: {} 没有数据", tagCode);
                } else {
                    for (int i = 0; i < pointValues.size(); i++) {
                        pointsSB.append(String.valueOf(pointValues.get(i)));

                        if (i != pointValues.size() - 1) {
                            pointsSB.append(",");
                        }
                    }
                    pointsSB.append("]");
                    pointsSB.append("\n");
                }
            // stringBuilder.append(pointsSB.toString());
        }
        return pointsSB.toString();
    }

    /**
     * 获取测点的信息，试验测点趋势的传参
     * @param cached_defectIds 诊断单list
     * @return
     */
    @RequestMapping("/device/tagsInfoList")
    public Map tagsInfoList(@RequestBody List<Object> cached_defectIds) {
        Map map = new HashMap();
        List<Map> resultMap = new ArrayList<>();
        for (int j = 0; j < cached_defectIds.size(); j++) {

            Object item = cached_defectIds.get(j);
            Integer incidentId = null;
            if (item instanceof Map) {
                Map<String, Object> dataMap = (Map<String, Object>) item;
                incidentId = (Integer) dataMap.get("incidentId");
            } else if (item instanceof Integer) {
                incidentId = (Integer) item;
            } else if (item instanceof String) {
                incidentId = Integer.valueOf(String.valueOf(item));
            }
            List<DefectIncidentInfo> defectIncidentInfoList = defectIncidentInfoMapper.selectDefectIncidentListById(incidentId);
            Date endTime = defectIncidentInfoList.get(0).getLastTime();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
                    .withZone(ZoneId.of("UTC")); // 关键：必须指定 UTC 时区
            String endTimeStr = formatter.format(endTime.toInstant());

            // 往前半小时
            String beginTimeStr = influxDBServiceJR.beforeXMinutes(endTimeStr);
            for (DefectIncidentInfo defectIncidentInfo : defectIncidentInfoList) {
                String type = defectIncidentInfo.getType();
                if (type.equals("测点")) {
                    Map itemMap = new HashMap();
                    //                     List<Double> pointValues = influxDBServiceJR.queryValues(defectIncidentInfo.getTagCode(), defectIncidentInfo.getSubsystemId(), beginTimeStr, endTimeStr);
                    itemMap.put("tagCode", defectIncidentInfo.getTagCode());
                    itemMap.put("subsystemId", defectIncidentInfo.getSubsystemId());
                    itemMap.put("beginTime", beginTimeStr);
                    itemMap.put("endTime", endTimeStr);
                    resultMap.add(itemMap);
                }
            }
        }
        map.put("cached_TagsTrendPara", resultMap);
        return map;
    }


    /**
     * 测点实际值信息
     * @param list 诊断单信息
     * @return
     */
    @RequestMapping("/device/tagsRealTime")
    public String tagsRealTimeValue(@RequestBody List<Object> list) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("\n\n## 测点数据\n\n");
        // 遍历同一个设备下的多个诊断单
        for (int j = 0; j < list.size(); j++) {
            Object item = list.get(j);
            Integer incidentId = null;
            if (item instanceof Map) {
                Map<String, Object> dataMap = (Map<String, Object>) item;
                incidentId = (Integer) dataMap.get("incidentId");
            } else if (item instanceof Integer) {
                incidentId = (Integer) item;
            } else if (item instanceof String) {
                incidentId = Integer.valueOf(String.valueOf(item));
            }
            // 获取: 故障模式、特征、测点
            List<DefectIncidentInfo> defectIncidentInfoList = defectIncidentInfoMapper.selectDefectIncidentListById(incidentId);
            // 结束时间
            Date endTime = defectIncidentInfoList.get(0).getLastTime();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
                    .withZone(ZoneId.of("UTC")); // 关键：必须指定 UTC 时区
            String endTimeStr = formatter.format(endTime.toInstant());

            // 往前半小时
            String beginTimeStr = influxDBServiceJR.beforeXMinutes(endTimeStr);
            StringBuilder pointsSB = new StringBuilder();
            // 从开始时间到结束时间，间隔一分钟
            pointsSB.append(String.format("\n从 %s 到 %s ", beginTimeStr, endTimeStr));
            String deviceNameStr = null;
            String deviceTypeStr = null;
            for (DefectIncidentInfo defectIncidentInfo : defectIncidentInfoList) {
                String type = defectIncidentInfo.getType();
                if (type.equals("测点")) {
                    log.info("tag: {}, {}, {}", defectIncidentInfo.getTagCode(), defectIncidentInfo.getSubsystemId(), defectIncidentInfo.getName());
                    // 查询测点数据
                    List<Double> pointValues = influxDBServiceJR.queryValues(defectIncidentInfo.getTagCode(), defectIncidentInfo.getSubsystemId(), beginTimeStr, endTimeStr);
                    pointsSB.append("\n");
                    pointsSB.append("测点名称：");
                    pointsSB.append(defectIncidentInfo.getName());
                    pointsSB.append(", 单位：");
                    pointsSB.append(defectIncidentInfo.getUnit());
                    pointsSB.append(", 严重度等级：");
                    pointsSB.append(defectIncidentInfo.getLevel());
                    pointsSB.append(", 测点值：[");
                    if (pointValues.size() == 0) {
                        log.warn("测点: {} 没有数据", defectIncidentInfo.getName());
                    } else {
                        for (int i = 0; i < pointValues.size(); i++) {
                            pointsSB.append(String.valueOf(pointValues.get(i)));

                            if (i != pointValues.size() - 1) {
                                pointsSB.append(",");
                            }
                        }
                        pointsSB.append("]");
                        pointsSB.append("\n");
                    }
                } else if (type.equals("设备") || type.equals("子系统") || type.equals("部件")) {
                    log.info("tag: {}, {}", type, defectIncidentInfo.getName());
                    deviceNameStr = defectIncidentInfo.getName();
                    deviceTypeStr = defectIncidentInfo.getType();
                }
            }
            stringBuilder.append(pointsSB.toString());
        }
        return stringBuilder.toString();
    }

    /**
     * 显示层级关系
     * @param list 诊断单list
     * @return
     */
    @RequestMapping("/device/graph/show")
    public String deviceGraphShow(@RequestBody List<Object> list) {
    // public String deviceGraphShow(@RequestParam String listStr) {
        StringBuilder stringBuilder = new StringBuilder();
        Set<Long> defectModeIdSets = new HashSet<>();
        StringBuilder tagsNamesString = new StringBuilder();
        // List<DefectIncidentInfo> list1 = new ArrayList<>();
        for (int j = 0; j < list.size(); j++) {
            Object item = list.get(j);
            Integer incidentId = null;
            if (item instanceof Map) {
                Map<String, Object> dataMap = (Map<String, Object>) item;
                incidentId = (Integer) dataMap.get("incidentId");
            }else if (item instanceof Integer){
                incidentId = (Integer) item;
            }else if (item instanceof String){
                incidentId = Integer.valueOf(String.valueOf(item));
            }
            // 获取故障模式--有可能故障模式不一样
            List<DefectIncidentInfo> defectModeIncidentInfoList = defectIncidentInfoMapper.selectDefectIncidentById(incidentId);
            for (DefectIncidentInfo defectModeIncidentInfo: defectModeIncidentInfoList) {
                if (defectModeIdSets.contains(defectModeIncidentInfo.getNodeId())) {
                    continue;
                }
                defectModeIdSets.add(defectModeIncidentInfo.getNodeId());
                // 故障模式的层级和RAG
                Integer defectModeId = Math.toIntExact(defectModeIncidentInfo.getNodeId());
                // 层级和测点
                String[] results = deviceHealthyService.deviceGraphShow(defectModeId);
                stringBuilder.append(results[0]);
                tagsNamesString.append(results[1]).append(",");
                stringBuilder.append("\n\n");
                // 推导图的伪代码
                stringBuilder.append(treeNodeService.deviceGraphCode(defectModeId));
            }
        }
        return stringBuilder.toString();
    }

    /**
     * 推导图-测试用
     * 故障模式的node_id
     **/
    @RequestMapping("/device/graph")
    public String deviceGraph(@RequestParam Integer nodeId) {
        return treeNodeService.deviceGraphCode(nodeId);
        // return root;
    }

    /**
     * 获取诊断单, 返回诊断单信息和其他信息
     * @param deviceRequest
     * @return
     */
    @RequestMapping("/device/healthy/v3")
    public Map deivceHealthyV3(@RequestBody DeviceRequest deviceRequest) {
        Map map = new HashMap();
        String deviceName = deviceRequest.getDevice();
        log.info("deviceName: {}", deviceName);
        Date[] dates =DateTool.getStartAndEndTime(deviceRequest);
        // 查询诊断单
        List<DefectIncidentInfo> list = defectIncidentInfoMapper.selectDefectIncidentIdListByName(deviceName, dates[0], dates[1]);
        // 给大模型显示的
        String llmMsg = list.size() == 0 ? "当前设备无诊断单": String.format("共%d条诊断单记录", list.size());
        map.put("llmMsg", llmMsg);
        List<Integer> defectIds = new ArrayList<>();
        for (DefectIncidentInfo defectIncidentInfo: list) {
            defectIds.add(defectIncidentInfo.getIncidentId());
        }
        map.put("cached_defectIds", defectIds);

        List<Map> resultMap = new ArrayList<>();
        for (int j = 0; j < defectIds.size(); j++) {

            Object item = defectIds.get(j);
            Integer incidentId = null;
            if (item instanceof Map) {
                Map<String, Object> dataMap = (Map<String, Object>) item;
                incidentId = (Integer) dataMap.get("incidentId");
            } else if (item instanceof Integer) {
                incidentId = (Integer) item;
            } else if (item instanceof String) {
                incidentId = Integer.valueOf(String.valueOf(item));
            }
            List<DefectIncidentInfo> defectIncidentInfoList = defectIncidentInfoMapper.selectDefectIncidentListById(incidentId);
            Date endTime = defectIncidentInfoList.get(0).getLastTime();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
                    .withZone(ZoneId.of("UTC")); // 关键：必须指定 UTC 时区
            String endTimeStr = formatter.format(endTime.toInstant());

            // 往前半小时
            String beginTimeStr = influxDBServiceJR.beforeXMinutes(endTimeStr);

            for (DefectIncidentInfo defectIncidentInfo : defectIncidentInfoList) {
                String type = defectIncidentInfo.getType();
                if (type.equals("测点")) {
                    Map itemMap = new HashMap();
                    //                     List<Double> pointValues = influxDBServiceJR.queryValues(defectIncidentInfo.getTagCode(), defectIncidentInfo.getSubsystemId(), beginTimeStr, endTimeStr);
                    itemMap.put("tagCode", defectIncidentInfo.getTagCode());
                    itemMap.put("subsystemId", defectIncidentInfo.getSubsystemId());
                    itemMap.put("beginTime", beginTimeStr);
                    itemMap.put("endTime", endTimeStr);
                    resultMap.add(itemMap);
                }
            }
        }
        map.put("cached_TagsTrendPara", resultMap);
        return map;
    }

    // 获取诊断单信息，对外的mcp方法，主要，得到诊断单
    @RequestMapping("/device/healthy/v2")
    public List<DefectIncidentInfo> deivceHealthyV2(@RequestBody DeviceRequest deviceRequest) {
        String deviceName = deviceRequest.getDevice();
        log.info("deviceName: {}", deviceName);
        Date[] dates =DateTool.getStartAndEndTime(deviceRequest);
        // 查询诊断单
        List<DefectIncidentInfo> list = defectIncidentInfoMapper.selectDefectIncidentIdListByName(deviceName, dates[0], dates[1]);
        return list;
    }

    // 设备健康度-v1版本
    @RequestMapping("/device/healthy/v1")
    public String deivceHealthy(@RequestBody DeviceRequest deviceRequest) {
        String deviceName = deviceRequest.getDevice();
        log.info("deviceName: {}", deviceName);
        Date[] dates =DateTool.getStartAndEndTime(deviceRequest);

        // 查询诊断单
        List<DefectIncidentInfo> list = defectIncidentInfoMapper.selectDefectIncidentIdListByName(deviceName, dates[0], dates[1]);

        StringBuilder stringBuilder = new StringBuilder();
        if (list.size() == 0) {
            return "设备信息正常";
        }

        Set<Long> defectModeIdSets = new HashSet<>();
        StringBuilder tagsNamesString = new StringBuilder();
        for (int j = 0; j < list.size(); j++) {
            // 获取故障模式--有可能故障模式不一样
            List<DefectIncidentInfo> defectModeIncidentInfoList = defectIncidentInfoMapper.selectDefectIncidentById(list.get(j).getIncidentId());
            for (DefectIncidentInfo defectModeIncidentInfo: defectModeIncidentInfoList) {
                if (defectModeIdSets.contains(defectModeIncidentInfo.getNodeId())) {
                    continue;
                }
                defectModeIdSets.add(defectModeIncidentInfo.getNodeId());
                // 故障模式的层级和RAG
                Integer defectModeId = Math.toIntExact(defectModeIncidentInfo.getNodeId());
                // 层级和测点
                String[] results = deviceHealthyService.deviceGraphShow(defectModeId);
                stringBuilder.append(results[0]);
                tagsNamesString.append(results[1]).append(",");
                stringBuilder.append("\n\n");
                // 推导图的伪代码
                stringBuilder.append(treeNodeService.deviceGraphCode(defectModeId));
            }

        }

        // map.put("message", stringBuilder.toString());
        // 诊断单id
        Integer incidentId = list.get(0).getIncidentId();

        log.info("incidentId: {}", incidentId);

        stringBuilder.append("\n\n## 测点数据\n\n");
        // 遍历同一个设备下的多个诊断单
        for (int j = 0; j < list.size(); j++) {
            DefectIncidentInfo defectIncident = list.get(j);
            // 获取: 故障模式、特征、测点
            List<DefectIncidentInfo> defectIncidentInfoList = defectIncidentInfoMapper.selectDefectIncidentListById(list.get(j).getIncidentId());
            // 结束时间
            Date endTime = defectIncident.getLastTime();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
                    .withZone(ZoneId.of("UTC")); // 关键：必须指定 UTC 时区
            String endTimeStr = formatter.format(endTime.toInstant());

            // 往前半小时
            String beginTimeStr = influxDBServiceJR.beforeXMinutes(endTimeStr);
            StringBuilder pointsSB = new StringBuilder();
            // 从开始时间到结束时间，间隔一分钟
            pointsSB.append(String.format("\n从 %s 到 %s ", beginTimeStr, endTimeStr));
            String deviceNameStr = null;
            String deviceTypeStr = null;
            for (DefectIncidentInfo defectIncidentInfo : defectIncidentInfoList) {
                String type = defectIncidentInfo.getType();
                if (type.equals("测点")) {
                    log.info("tag: {}, {}, {}", defectIncidentInfo.getTagCode(), defectIncidentInfo.getSubsystemId(), defectIncidentInfo.getName());
                    // 查询测点数据
                    List<Double> pointValues = influxDBServiceJR.queryValues(defectIncidentInfo.getTagCode(), defectIncidentInfo.getSubsystemId(), beginTimeStr, endTimeStr);
                    pointsSB.append("\n");
                    pointsSB.append("测点名称：");
                    pointsSB.append(defectIncidentInfo.getName());
                    pointsSB.append(", 单位：");
                    pointsSB.append(defectIncidentInfo.getUnit());
                    pointsSB.append(", 严重度等级：");
                    pointsSB.append(defectIncidentInfo.getLevel());
                    pointsSB.append(", 测点值：[");
                    if (pointValues.size() == 0) {
                        log.warn("测点: {} 没有数据", defectIncidentInfo.getName());
                    } else {
                        for (int i = 0; i < pointValues.size(); i++) {
                            pointsSB.append(String.valueOf(pointValues.get(i)));

                            if (i != pointValues.size() - 1) {
                                pointsSB.append(",");
                            }
                        }
                        pointsSB.append("]");
                        pointsSB.append("\n");
                    }
                } else if (type.equals("设备") || type.equals("子系统") || type.equals("部件")) {
                    log.info("tag: {}, {}", type, defectIncidentInfo.getName());
                    deviceNameStr = defectIncidentInfo.getName();
                    deviceTypeStr = defectIncidentInfo.getType();
                }
            }

            stringBuilder.append(pointsSB.toString());
            if (deviceNameStr == null) {
                log.warn("没有设备: {}", deviceName);
                // map.put("message", "没有设备");
            } else {
                String returnMsg = String.format("%s: %s, 关联的测点：%s", deviceTypeStr, deviceNameStr, pointsSB.toString());
                log.info("returnMsg: {}", returnMsg);
                // map.put("message", returnMsg);
                    /*
                    String llmStr = String.format(this.getPromptStr(), deviceNameStr,pointsSB.toString());
                    log.info("llmStr: {}", llmStr);
                    JSONObject objectMap = new JSONObject();
                    objectMap.put("model", promptModel);
                    objectMap.put("stream", false);
                    objectMap.put("prompt", llmStr);
                    com.changgeng.prompt.pojo.LLMResult result = this.ollamaClient.generate(objectMap);
                    if (result.getResponse() == null) {
                        map.put("message", "结果空");
                    }
                    log.info(result.getResponse());
                    map.put("message", result.getResponse());
                    */
            }
        }

        stringBuilder.append("\n\n");
        stringBuilder.append(deviceHealthyService.deviceRag(tagsNamesString.toString()));
        stringBuilder.append("\n");
        return stringBuilder.toString();
    }



    private String getPromptStr() {
        if (promptStr == null) {
            StringBuilder content = new StringBuilder();
            try {
                BufferedReader bufferedReader = new BufferedReader(new FileReader(promptPath));
                while (true) {
                    String line = bufferedReader.readLine();
                    if (line == null) {
                        break;
                    }
                    content.append(line).append('\n');
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e2) {
                e2.printStackTrace();
            }
            promptStr = content.toString();
        }
        return promptStr;
    }

    // 安全获取 Map 中的值，防止空指针异常
    private static String safeGet(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : value.toString();
    }

    static String getTableByList(List<Map<String, Object>> list, String instanceName) {
        // 1. 定义分组键（将三个字段拼接成一个唯一的 Key）
        // 2. 按照这个 Key 进行分组
        list = list.stream().filter(one -> one.get("tagType").toString().equals("模拟量")).collect(Collectors.toList());
        Map<String, List<Map<String, Object>>> groupedMap = list.stream()
                .collect(Collectors.groupingBy(map ->
                        String.join("|",
                                safeGet(map, "unitName"),
                                safeGet(map, "assetName"),
                                safeGet(map, "attrName"),
                                safeGet(map, "srcTagName")
                        )
                ));

        // 3. 遍历分组后的结果，生成表格数据
        List<Map<String, Object>> tableRows = new ArrayList<>();

        for (List<Map<String, Object>> group : groupedMap.values()) {
            // 取第一条数据作为该组的表头信息
            Map<String, Object> firstRow = group.get(0);

            // 构建表格的一行
            Map<String, Object> tableRow = new LinkedHashMap<>(); // 使用 LinkedHashMap 保证字段顺序
            tableRow.put("unitName", firstRow.get("unitName"));
            tableRow.put("assetName", firstRow.get("assetName"));
            tableRow.put("attrName", firstRow.get("attrName"));
            tableRow.put("tagDesc", firstRow.get("tagDesc"));

            tableRows.add(tableRow);
        }

        // 4. 打印或输出结果
        StringBuilder sb = new StringBuilder();
        sb.append("与").append(instanceName).append("相关的测点关系如下:\n");
        sb.append("| 机组名称 | 设备名称 | 特征名称 | 测点描述 |\n");
        sb.append("|---|---|---|---|---|\n");
        for (Map<String, Object> row : tableRows) {
            // 使用 append 方法拼接，替代 printf
            sb.append("| ")
                    .append(row.get("unitName")).append(" | ")
                    .append(row.get("assetName")).append(" | ")
                    .append(row.get("attrName")).append(" | ")
                    .append(row.get("tagDesc")).append(" |\n");
        }
        return sb.toString();
    }
}
