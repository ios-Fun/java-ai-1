package com.changgeng.service;

import com.changgeng.client.DamCoreClient;
import com.changgeng.client.DamExtClient;
import com.changgeng.mapper.AlarmTableMapper;
import com.changgeng.mapper.DefectIncidentInfoMapper;
import com.changgeng.mapper.DefectIncidentMapper;
import com.changgeng.model.DefectIncidentInfo;
import com.changgeng.pojo.*;
import com.changgeng.tree.TreeNodePath;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.changgeng.tool.CommonTool.mixedSimilarity2;

@Service
@Slf4j
public class UnitService {

    @Autowired
    DamCoreClient damCoreClient;
    @Autowired
    DamExtClient damExtClient;
    @Autowired
    AlarmTableMapper alarmTableMapper;
    @Autowired
    DefectIncidentMapper defectIncidentMapper;
    @Autowired
    DefectIncidentInfoMapper defectIncidentInfoMapper;

    public List<Map> getItems(Integer unitId, String type) {
        List<Map> list = damExtClient.getItems(unitId, type);
        return list;
    }

    public List<AlarmTable> getAlarmList(AlarmListRequest request) {
        List<AlarmTable> alarmList = alarmTableMapper.selectAlarmList(request);
        return alarmList;
    }

    public List<SystemIncidentInfo> getSystemIncidentList(SystemIncidentRequest request) {
        List<SystemIncidentInfo> list = defectIncidentMapper.selectSystemIncidentList(request);
        return list;
    }

    public List<SystemIncidentInfo> getSubSystemIncidentList(SystemIncidentRequest request) {
        List<SystemIncidentInfo> list = defectIncidentMapper.selectSubSystemIncidentList(request);
        return list;
    }

    /**
     * 根据已匹配的机组列表，查询每个机组的诊断单
     *
     * @param matchedUnits matchUnits 返回的已匹配机组列表（每项含 unitId、unitName）
     * @param dates        时间范围 [startTime, endTime]
     * @param closed       是否已关闭（可选）
     * @return 每个机组的诊断单列表，每项含 unitId、unitName、incidents（完整 DefectIncidentInfo）
     */
    public List<Map<String, Object>> getUnitIncidentList(List<Map> matchedUnits, Date[] dates, Boolean closed) {
        return matchedUnits.stream()
                .filter(map -> (Boolean) map.get("matched") != false)
                .map(map -> {
                    Integer unitId = (Integer) map.get("unitId");
                    List<DefectIncidentInfo> incidents = defectIncidentInfoMapper.selectDefectIncidentIdListByUnit(unitId, dates[0], dates[1], closed);
                    if (incidents.isEmpty()) return null;
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("unitId", unitId);
                    m.put("unitName", map.get("unitName"));
                    m.put("incidents", incidents);
                    return m;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /** level 排序优先级：严重 > 较严重 > 中度 > 轻微 > 征兆，未知值排最后 */
    private static final List<String> LEVEL_ORDER = Arrays.asList("严重", "较严重", "中度", "轻微", "征兆");

    private static int levelRank(String level) {
        int idx = LEVEL_ORDER.indexOf(level);
        return idx == -1 ? LEVEL_ORDER.size() : idx;
    }

    /**
     * 根据已匹配的机组列表，查询每个机组的诊断单（精简版）
     * 每条诊断单只保留核心字段，并统计关闭/未关闭数量，按 level 严重程度排序
     *
     * @param matchedUnits matchUnits 返回的已匹配机组列表
     * @param dates        时间范围 [startTime, endTime]
     * @param closed       是否已关闭（可选）
     * @return 精简版机组诊断单列表，每项含 unitId、unitName、closedCount、unclosedCount、incidents
     */
    public List<Map<String, Object>> getBriefIncidentMap(List<Map> matchedUnits, Date[] dates, Boolean closed) {
        return getUnitIncidentList(matchedUnits, dates, closed).stream().map(unitMap -> {
            @SuppressWarnings("unchecked")
            List<DefectIncidentInfo> incidents = (List<DefectIncidentInfo>) unitMap.get("incidents");
            Date now = new Date();
            long stillTriggerCount = incidents.stream().filter(i -> now.getTime() - i.getLastTime().getTime() <= 10 * 60 * 1000L ).count();
            long noTriggerCountCount = incidents.size() - stillTriggerCount;

            List<Map<String, Object>> briefIncidents = incidents.stream().map(i -> {
                Boolean isStillTrigger = now.getTime() - i.getLastTime().getTime() <= 10 * 60 * 1000L;
                Map<String, Object> m = new LinkedHashMap<>();
//                m.put("dataId", i.getDataId());
                m.put("name", i.getName());
                m.put("severity", i.getSeverity());
//                m.put("maxSeverity", i.getMaxSeverity());
//                m.put("realSeverity", i.getRealSeverity());
                m.put("lastTime", i.getLastTime());
                m.put("type", i.getType());
//                m.put("nodeId", i.getNodeId());
                m.put("level", i.getLevel());
                m.put("incidentId", i.getIncidentId());
//                m.put("maxTime", i.getMaxTime());
                m.put("isStillTrigger", isStillTrigger);
                m.put("maxDefectSeverityName", i.getMaxDefectSeverityName());
                m.put("firstOccurredDateTime", i.getFirstOccurredDateTime());
//                m.put("closed", i.getClosed());
                return m;
            })
            .sorted(Comparator.comparingInt(m -> levelRank((String) m.get("level"))))
            .collect(Collectors.toList());

            List<Map> assets = getItems((Integer) unitMap.get("unitId"), "设备");
            Double unitHealthy = 100.0;
            AtomicInteger assetIndicidentCount = new AtomicInteger();
            if (!assets.isEmpty() && !incidents.isEmpty()){
                double totalDeduction = incidents.stream()
                        .filter(o -> "设备".equals(o.getType()))
                        .mapToDouble(o -> {
                            assetIndicidentCount.addAndGet(1);
                            switch (o.getLevel()) {
                                case "严重":
                                    return 10;
                                case "较严重":
                                    return 7;
                                case "中度":
                                    return 4;
                                case "轻微":
                                    return 2;
                                case "征兆":
                                    return 1;
                                default:
                                    return 0;
                            }
                        })
                        .sum();
                log.info("totalDeduction:{}, assetCount:{}", totalDeduction, assetIndicidentCount.get());
                unitHealthy = 100.0 - totalDeduction * 0.4 - assetIndicidentCount.get() * 0.3;
            }

            Map<String, Object> briefUnitMap = new LinkedHashMap<>();
            briefUnitMap.put("unitId", unitMap.get("unitId"));
            briefUnitMap.put("unitName", unitMap.get("unitName"));
            briefUnitMap.put("unitHealthy", unitHealthy);
            briefUnitMap.put("stillTriggerCount", stillTriggerCount);
            briefUnitMap.put("noTriggerCountCount", noTriggerCountCount);
            briefUnitMap.put("incidents", briefIncidents);
            return briefUnitMap;
        }).collect(Collectors.toList());
    }

    public List<Map> matchUnits(String unitName) {
        List<Map> allUnits = (List<Map>) damCoreClient.selectAllUnit().get("data");
        List<Map> result = new ArrayList<>();
        if (allUnits != null) {
            for (Map<String, Object> unit : allUnits) {
                String uName = (String) unit.get("unitName");
                if (uName != null && uName.contains(unitName)) {
//                    Map<String, Object> unitInfo = new HashMap<>();
//                    unitInfo.put("unitId", unit.get("unitId"));
//                    unitInfo.put("unitName", uName);
//                    result.add(unitInfo);
                    unit.put("matched", true);
                }else unit.put("matched", false);
            }
        }
        return allUnits;
    }

    public Object getPathUnderUnit(PathUnderUnitRequest request) {
        Integer unitId = request.getUnitId();
        if (unitId == null) return "unitId 不能为空";
        List<Integer> incidentIds = request.getIncidentIds();
        Set<String> excludedTypes = new HashSet<>(Arrays.asList("特征", "测点", "故障模式"));
        List<Long> result = incidentIds.stream()
                .map(defectIncidentInfoMapper::selectDefectIncidentInfoByIncidentId)
                .flatMap(optional -> optional.stream())
                .filter(i -> !excludedTypes.contains(i.getType()))
                .map(DefectIncidentInfo::getNodeId)
                .collect(Collectors.toList());
        List<List<String>> paths = result.parallelStream()
                .map(damExtClient::getPathByNodeId)
                .collect(Collectors.toList());
        return buildTree(paths);
    }

    public static TreeNodePath buildTree(List<List<String>> paths) {
        if (paths.isEmpty()) return null;
        String rootName = paths.get(0).get(0);
        TreeNodePath root = new TreeNodePath(rootName);

        for (List<String> path : paths) {
            TreeNodePath current = root;
            for (int i = 1; i < path.size(); i++) {
                current = current.addChild(path.get(i));
            }
        }
        return root;
    }

    public Map<String, Object> getAlarmListStatistics(AlarmListRequest request) {
        List<AlarmTable> alarmList = alarmTableMapper.selectAlarmList(request);

        Map<String, Object> alarmListRes = new LinkedHashMap<>();

        Map<String, String> descriptionMap = new LinkedHashMap<>();
        descriptionMap.put("严重度", "由测点的实际值，高报值，高高报值，低报值，低低报值联合推演出的一个数值，用于表示该测点偏离正常数值的程度。");
        descriptionMap.put("超限（低）", "当测点的严重度，在一定时间内连续超过某个阈值达到一定数量，并且实际值低于某个设定阈值，触发超超限（低）报警。");
        descriptionMap.put("超限（高）", "当测点的严重度，在一定时间内连续超过某个阈值达到一定数量，并且实际值高于某个设定阈值，触发超超限（高）报警。");
        descriptionMap.put("综合", "当测点的严重度，在一定时间内连续超过某个阈值达到一定数量，并且当前严重度高于某个设定阈值，触发综合报警。");
        descriptionMap.put("一级预警", "当测点的实时严重度大于预设的一级严重度，触发一级报警。");
        descriptionMap.put("二级预警", "当测点的实时严重度大于预设的二级严重度，触发二级报警。");
        descriptionMap.put("开关量异常", "当开关量测点的值处于不正确数值时，触发开关量报警。");
        descriptionMap.put("严重度报警", "当测点的实时严重度，在一定时间内，累计触发达到一定数量，或严重度超过某个阈值时，触发严重度报警。");
        alarmListRes.put("description", descriptionMap);

        if (alarmList == null || alarmList.isEmpty()) {
            return new HashMap<>();
        }

        Map<String, List<AlarmTable>> groupedByType = alarmList.stream()
                .filter(alarm -> alarm.getAlarmType() != null)
                .collect(Collectors.groupingBy(AlarmTable::getAlarmType));

        for (Map.Entry<String, List<AlarmTable>> entry : groupedByType.entrySet()) {
            String alarmType = entry.getKey();
            List<AlarmTable> alarms = entry.getValue();

            Map<String, Object> typeDetail = new LinkedHashMap<>();
            typeDetail.put("count", alarms.size());

            Map<String, Map<String, String>> tagMap = new LinkedHashMap<>();
            for (AlarmTable alarm : alarms) {
                Map<String, String> tagInfo = new LinkedHashMap<>();
                tagInfo.put("value", alarm.getActual());
                if (alarm.getUnit() != null) {
                    tagInfo.put("unit", alarm.getUnit());
                }
                tagInfo.put("isClosed", alarm.getClosed().toString());

                tagMap.put(alarm.getTagDescription(), tagInfo);
            }
            typeDetail.put("tagName", tagMap);

            alarmListRes.put(alarmType, typeDetail);
        }

        return alarmListRes;
    }

    public List<Map> getInstanceOfUnit(Integer unitId, String keyword, String instanceType) {
        if(unitId == null || instanceType == null) return new ArrayList<>();
        List<Map> items = getItems(unitId, instanceType);
        if(items.isEmpty()) return new ArrayList<>();
        return items.parallelStream()
                .filter(item -> {
                    String itemName = item.get("名称").toString();
                    double similarity = mixedSimilarity2(keyword, itemName);
                    if (similarity > 0.4) return true;
                    return false;
                })
                .collect(Collectors.toList());
    }
}
