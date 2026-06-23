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
     * 机组健康度分析接口
     * 根据机组名和时间范围查询诊断单信息，返回故障模式层级、RAG 知识检索结果和测点数据
     * 
     * 请求参数说明：
     * - unitName: 机组名称（必填），如 "京燃"
     * - startTime: 开始时间（可选），如 "2024-01-01T00:00:00+08:00"
     * - endTime: 结束时间（可选），如 "2024-01-07T23:59:59+08:00"
     * - num: 时间跨度数值（可选），与 timeUnit 配合使用，如 "7"
     * - timeUnit: 时间单位（可选），可选值：day/week/month/year，如 "week" 表示往前推 7 天
     * 
     * 使用说明：
     * 1. 不传时间参数时，默认查询最近 7 天的数据
     * 2. 可传 startTime 和 endTime 指定具体时间范围
     * 3. 可传 num 和 timeUnit 指定相对时间，如 num=7, timeUnit="week" 表示往前推 7 周
     *
     * @param request 机组健康度请求参数
     * @return 故障模式层级、RAG 检索结果和测点数据
     */
    @RequestMapping("/unit/healthy")
    public String unitHealthy(@RequestBody UnitHealthyRequest request) {
        // RAG放在设备层级下不？
        Boolean ragIN = false;

        String unitName = request.getUnitName();
        List<UnitIncidentDTO> unitIncidentList = getUnitIncidentMap(request);
        if (unitIncidentList.isEmpty()) return "机组信息正常";

        StringBuilder stringBuilder = new StringBuilder();
        String ragText = "";
        // 按机组分组处理
        for (UnitIncidentDTO unitDTO : unitIncidentList) {
            Integer unitId = unitDTO.getUnitId();
            List<DefectIncidentInfo> incidentList = unitDTO.getIncidents();
            log.info("unitId: {}, unitName: {}, 诊断单数量: {}", unitId, unitDTO.getUnitName(), incidentList.size());
            stringBuilder.append("=== 机组 ").append(unitDTO.getUnitName()).append("(").append(unitId).append(") ===\n");
            List<String> assetNames = new ArrayList<>();

            for (DefectIncidentInfo incident : incidentList) {
                if (!"设备".equals(incident.getType()) || assetNames.contains(incident.getName())) {
                    continue;
                }
                // 查询该诊断单下的所有故障模式
                List<DefectIncidentInfo> faultModeList = defectIncidentInfoMapper.selectFaultModeListById(incident.getIncidentId());
                for (DefectIncidentInfo faultMode : faultModeList) {
                    String[] results = deviceHealthyService.deviceGraphShow(Math.toIntExact(faultMode.getNodeId()));
                    stringBuilder.append("--- 设备: ").append(incident.getName())
                            .append(" | 故障模式: ").append(faultMode.getName()).append(" ---\n");
                    stringBuilder.append(results[0]).append("\n");
                    ragText += deviceHealthyService.deviceRag(results[1])+"\n";
                    if(ragIN) stringBuilder.append(deviceHealthyService.deviceRag(results[1])).append("\n");
                }

                Integer incidentId = incident.getIncidentId();
                log.info("incidentId: {}", incidentId);

                List<DefectIncidentInfo> defectIncidentInfoList = defectIncidentInfoMapper.selectDefectIncidentListById(incidentId);
                if (defectIncidentInfoList.size() == 0) {
                    log.warn("诊断单 {} 无相关测点信息", incidentId);
                    assetNames.add(incident.getName());
                    continue;
                }

                Date endTime = incident.getLastTime();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
                        .withZone(ZoneId.of("UTC"));
                String endTimeStr = formatter.format(endTime.toInstant());

                String beginTimeStr = influxDBServiceJR.beforeXMinutes(endTimeStr);
                StringBuilder pointsSB = new StringBuilder();
                String deviceNameStr = null;
                String deviceTypeStr = null;

                for (DefectIncidentInfo defectIncidentInfo : defectIncidentInfoList) {
                    String type = defectIncidentInfo.getType();
                    if (type.equals("测点")) {
                        log.info("tag: {}, {}, {}", defectIncidentInfo.getTagCode(), defectIncidentInfo.getSubsystemId(), defectIncidentInfo.getName());
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
                            pointsSB.append("]");
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
                    } else if (type.equals("机组") || type.equals("设备") || type.equals("子系统") || type.equals("部件")) {
                        log.info("tag: {}, {}", type, defectIncidentInfo.getName());
                        deviceNameStr = defectIncidentInfo.getName();
                        deviceTypeStr = defectIncidentInfo.getType();
                    }
                }

                if (deviceNameStr == null) {
                    log.warn("没有设备: {}", unitName);
                } else {
                    String returnMsg = String.format("%s: %s, 关联的测点：%s", deviceTypeStr, deviceNameStr, pointsSB.toString());
                    log.info("returnMsg: {}", returnMsg);
                    stringBuilder.append(returnMsg).append("\n");
                }

                assetNames.add(incident.getName());
            }
        }
        if(!ragIN) stringBuilder.append(ragText);
        return stringBuilder.toString();
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
     * 机组测点关系接口
     * 根据机组名称信息信息，返回机组名称，设备名称，特征名称，测点描述数据
     * <p>
     * 请求参数说明：
     * - assetName: 设备名称（必填），如 "汽轮机"
     *
     * @param unitName 设备名称
     * @return 机组名称，设备名称，特征名称，测点描述数据
     */
    @RequestMapping("/unit/tag")
    public String unitTag(@RequestParam String unitName) {
        List<Map<String, Object>> units = damExtClient.getUnitsOrAssetsProps(null, unitName);
        return getTableByList(units, unitName);
    }

    /**
     * 根据机组名模糊匹配机组，按 unitId 分组查询诊断单信息
     *
     * @param request 机组健康度请求参数（含 unitName、时间范围等）
     * @return 机组诊断单列表，每项包含 unitId、unitName、incidents
     */
    @RequestMapping("/unit/selectIncidents")
    public List<UnitIncidentDTO> getUnitIncidentMap(@RequestBody UnitHealthyRequest request) {
        String unitName = request.getUnitName();
        Date[] dates = DateTool.getStartAndEndTime(request);
        List<Map<String,Object>> allUnits = (List<Map<String, Object>>) damCoreClient.selectAllUnit().get("data");

        List<UnitIncidentDTO> result = new ArrayList<>();
        if (allUnits != null) {
            for (Map<String, Object> unit : allUnits) {
                String uName = (String) unit.get("unitName");
                if (uName != null && uName.contains(unitName)) {
                    Integer unitId = (Integer) unit.get("unitId");
                    List<DefectIncidentInfo> unitList = defectIncidentInfoMapper.selectDefectIncidentIdListByUnit(unitId, dates[0], dates[1]);
                    if (unitList.size() > 0) {
                        UnitIncidentDTO dto = new UnitIncidentDTO();
                        dto.setUnitId(unitId);
                        dto.setUnitName(uName);
                        dto.setIncidents(unitList);
                        result.add(dto);
                    }
                }
            }
        }
        return result;
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
     * 显示层级关系
     * @param nodeId 故障模式nodeId
     * @return
     */
    @RequestMapping("/device/graph/show")
    public String deviceGraphShow(@RequestParam List<DefectIncidentInfo> list) {
        StringBuilder stringBuilder = new StringBuilder();
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

    @RequestMapping("/device/healthy/v2")
    public List<DefectIncidentInfo> deivceHealthyV2(@RequestBody DeviceRequest deviceRequest) {
        String deviceName = deviceRequest.getDevice();
        log.info("deviceName: {}", deviceName);
        Date[] dates =DateTool.getStartAndEndTime(deviceRequest);
        // 查询诊断单
        List<DefectIncidentInfo> list = defectIncidentInfoMapper.selectDefectIncidentIdListByName(deviceName, dates[0], dates[1]);
        return list;
    }

    // 设备健康度
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

        stringBuilder.append("\n\n## 测年数据\n\n");
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

    private static String getTableByList(List<Map<String, Object>> list, String instanceName) {
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
