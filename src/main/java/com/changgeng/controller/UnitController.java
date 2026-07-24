package com.changgeng.controller;


import com.changgeng.client.DamCoreClient;
import com.changgeng.client.DamExtClient;
import com.changgeng.client.DamSdkClient;
import com.changgeng.client.OllamaClient;
import com.changgeng.common.result.Result;
import com.changgeng.handler.InfluxDBServiceJR;
import com.changgeng.handler.ReadCSVService;
import com.changgeng.mapper.AlarmDefectsMapper;
import com.changgeng.mapper.AlarmTableMapper;
import com.changgeng.mapper.DefectIncidentInfoMapper;
import com.changgeng.mapper.DefectIncidentMapper;
import com.changgeng.model.DefectIncidentInfo;
import com.changgeng.pojo.*;
import com.changgeng.service.DeviceHealthyService;
import com.changgeng.service.UnitService;
import com.changgeng.tool.DateTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static com.changgeng.controller.DeviceHealthyController.getTableByList;

@RestController
@RequestMapping("/ai/unit")
@Slf4j
public class UnitController {

    @Autowired
    UnitService unitService;
    @Autowired
    DamExtClient damExtClient;
    @Autowired
    DefectIncidentInfoMapper defectIncidentInfoMapper;
    @Autowired
    DefectIncidentMapper defectIncidentMapper;
    @Autowired
    AlarmDefectsMapper alarmDefectsMapper;
    @Autowired
    AlarmTableMapper alarmTableMapper;
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
    @RequestMapping("/healthy")
    public String unitHealthy(@RequestBody UnitHealthyRequest request) {
        // RAG放在设备层级下不？
        Boolean ragIN = false;

        String unitName = request.getUnitName();
        List<Map> allUnits = unitService.matchUnits(unitName);
        Date[] dates = DateTool.getStartAndEndTime(request);
        Boolean closed = request.getClosed();

        List<Map<String, Object>> unitIncidentList = unitService.getUnitIncidentList(allUnits, dates, closed);
        if (unitIncidentList.isEmpty()) return "未查到机组信息，或该机组下无诊断单。";

        StringBuilder stringBuilder = new StringBuilder();
        String ragText = "";
        // 按机组分组处理
        for (Map<String, Object> unitMap : unitIncidentList) {
            Integer unitId = (Integer) unitMap.get("unitId");
            String unitNameStr = (String) unitMap.get("unitName");
            List<DefectIncidentInfo> incidentList = (List<DefectIncidentInfo>) unitMap.get("incidents");
            log.info("unitId: {}, unitName: {}, 诊断单数量: {}", unitId, unitNameStr, incidentList.size());
            stringBuilder.append("=== 机组 ").append(unitNameStr).append("(").append(unitId).append(") ===\n");
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
     * 查询机组下指定层级的节点列表
     * 根据机组节点ID和类型，查询该机组下属于指定类型的所有节点属性
     * 请求参数说明：
     * @param unitId 机组节点ID
     * @param type   节点类型标签如 "系统"、"子系统"、"设备"、"部件"、"测点"
     * @return 节点属性列表，每项包含编码、名称、描述、类别等字段
     */
    @RequestMapping("/getItems")
    public Result getItems(@RequestParam Integer unitId, @RequestParam String type) {
        List<Map> list = unitService.getItems(unitId, type);
        return Result.success(list);
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
    @RequestMapping("/tag")
    public String unitTag(@RequestParam String unitName) {
        List<Map<String, Object>> units = damExtClient.getUnitsOrAssetsProps(null, unitName);
        return getTableByList(units, unitName);
    }

    /**
     * 根据机组名模糊匹配机组，按 unitId 分组查询诊断单信息
     *
     * @param request 机组健康度请求参数（含 unitName、时间范围、单子是否已关闭等）
     * @return 机组诊断单列表，每项包含 unitId、unitName、incidents
     */
    @RequestMapping("/selectIncidents")
    public Result getUnitIncidentMap(@RequestBody UnitHealthyRequest request) {
        String unitName = request.getUnitName();
        List<Map> allUnits = unitService.matchUnits(unitName);
        Date[] dates = DateTool.getStartAndEndTime(request);
        Boolean closed = request.getClosed();

        List<Map<String, Object>> result = unitService.getBriefIncidentMap(allUnits, dates, closed);
        return result.isEmpty()
                ? Result.success("未查到相关机组信息，当前数据中存在机组：" + allUnits)
                : Result.success(result);
    }

    /**
     * 测点报警单列表查询接口
     * 根据传入的多个条件参数查询 alarmtable 表中的告警信息，支持多维度筛选
     *
     * 请求参数说明：
     * - tagName: 测点名称（可选），用于模糊匹配 tagname 字段
     * - tagSourceName: 测点来源名称（可选），用于筛选特定来源的测点
     * - startTime: 开始时间（可选），查询 firsttouchtime >= 该时间的告警记录
     * - endTime: 结束时间（可选），查询 lasttouchtime <= 该时间的告警记录
     * - assetNumber: 设备编号（可选），用于筛选特定设备的告警
     * - dataType: 数据类型（可选），如 "告警"、"缺陷" 等
     * - tagId: 测点ID（可选），精确查询某个测点的告警
     * - monitorPointId: 监测点ID（可选），查询指定监测点的告警
     * - unitId: 机组ID（可选），查询特定机组下的所有告警
     * - closed: 是否已关闭（可选，默认false），true表示查询已关闭的告警，false表示查询未关闭的告警
     * - AI: 是否为AI请求（可选，默认true）
     *
     * @param request 告警列表查询请求参数
     * @return 告警信息列表，包含测点名称、告警类型、严重度等级、触发时间、处理状态等字段
     */
    @RequestMapping("/getAlarmList")
    public Result getAlarmList(@RequestBody AlarmListRequest request) {
        return Result.success(unitService.getAlarmList(request));
    }

    /**
     * 系统诊断单列表查询接口
     * 根据传入的多个条件参数查询 systemincident 表中的系统诊断单信息，支持多维度筛选
     *
     * 请求参数说明：
     * - systemId: 系统ID（可选），查询特定系统的诊断单
     * - unitId: 机组ID（可选），查询特定机组下的系统诊断单
     * - startTime: 开始时间（可选），与 endTime 配合使用，查询触发时间在该范围内的诊断单
     * - endTime: 结束时间（可选），与 startTime 配合使用，查询触发时间在该范围内的诊断单
     * - currentStatus: 当前状态（可选），如 "待处理"、"处理中"、"已关闭" 等
     * - closed: 是否已关闭（可选，默认false），true表示查询已关闭的诊断单，false表示查询未关闭的诊断单
     *
     * @param request 系统诊断单查询请求参数
     * @return 系统诊断单列表，包含诊断单ID、严重度、当前状态、触发时间、处理时间等字段
     */
    @RequestMapping("/getSystemIncidentList")
    public Result getSystemIncidentList(@RequestBody SystemIncidentRequest request) {
        return Result.success(unitService.getSystemIncidentList(request));
    }

    /**
     * 子系统诊断单列表查询接口
     * 根据传入的多个条件参数查询 subsystemincident 表中的子系统诊断单信息，支持多维度筛选
     *
     * 请求参数说明：
     * - subSystemId: 系统ID（可选），查询特定子系统的诊断单
     * - unitId: 机组ID（可选），查询特定机组下的子系统诊断单
     * - startTime: 开始时间（可选），与 endTime 配合使用，查询触发时间在该范围内的诊断单
     * - endTime: 结束时间（可选），与 startTime 配合使用，查询触发时间在该范围内的诊断单
     * - currentStatus: 当前状态（可选），如 "待处理"、"处理中"、"已关闭" 等
     * - closed: 是否已关闭（可选，默认false），true表示查询已关闭的诊断单，false表示查询未关闭的诊断单
     *
     * @param request 子系统诊断单查询请求参数
     * @return 子系统诊断单列表，包含诊断单ID、严重度、当前状态、触发时间、处理时间等字段
     */
    @RequestMapping("/getSubSystemIncidentList")
    public Result getSubSystemIncidentList(@RequestBody SystemIncidentRequest request) {
        return Result.success(unitService.getSubSystemIncidentList(request));
    }

    /**
     * 获取机组下诊断单关联节点的层级路径树
     * 根据机组ID和诊断单ID列表，查询各诊断单对应节点的完整层级路径（从机组到节点），
     * 过滤掉"特征"、"测点"、"故障模式"类型的节点后，将所有路径合并构建为树形结构返回。
     * @param request 请求参数，包含：
     *                - unitId: 机组ID（必填）
     *                - incidentIds: 诊断单ID列表
     * @return 树形结构，根节点为机组名称，子节点为各层级节点名称
     */
    @RequestMapping("/getPathUnderUnit")
    public Result getPathUnderUnit(@RequestBody PathUnderUnitRequest request) {
        return Result.success(unitService.getPathUnderUnit(request));
    }
}
