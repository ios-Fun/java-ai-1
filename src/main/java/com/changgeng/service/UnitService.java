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
import java.util.stream.Collectors;

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
            long closedCount = incidents.stream().filter(i -> Integer.valueOf(1).equals(i.getClosed())).count();
            long unclosedCount = incidents.size() - closedCount;

            List<Map<String, Object>> briefIncidents = incidents.stream().map(i -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("dataId", i.getDataId());
                m.put("name", i.getName());
                m.put("severity", i.getSeverity());
                m.put("maxSeverity", i.getMaxSeverity());
                m.put("realSeverity", i.getRealSeverity());
                m.put("lastTime", i.getLastTime());
                m.put("type", i.getType());
                m.put("nodeId", i.getNodeId());
                m.put("level", i.getLevel());
                m.put("incidentId", i.getIncidentId());
                m.put("maxTime", i.getMaxTime());
                m.put("closed", i.getClosed());
                return m;
            })
            .sorted(Comparator.comparingInt(m -> levelRank((String) m.get("level"))))
            .collect(Collectors.toList());

            Map<String, Object> briefUnitMap = new LinkedHashMap<>();
            briefUnitMap.put("unitId", unitMap.get("unitId"));
            briefUnitMap.put("unitName", unitMap.get("unitName"));
            briefUnitMap.put("closedCount", closedCount);
            briefUnitMap.put("unclosedCount", unclosedCount);
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
        List<List<String>> paths = result.stream()
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
}
