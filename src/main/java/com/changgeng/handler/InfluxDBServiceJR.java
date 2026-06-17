package com.changgeng.handler;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.support.ExcelTypeEnum;
import com.changgeng.config.InfluxDBConfig;
import com.changgeng.pojo.InfluxdbValue;
import com.changgeng.pojo.UserCsvDTO;
import lombok.extern.java.Log;
import okhttp3.OkHttpClient;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBException;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.BatchPoints;
import org.influxdb.dto.Point;
import org.influxdb.dto.Query;
import org.influxdb.dto.QueryResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * @Author henry
 * @Date 2024/1/17 9:26
 * @Version 1.0
 */

@Service
@Log
public class InfluxDBServiceJR {
    @Autowired
    InfluxDBConfig influxDBConfig;

    @Autowired
    ReadCSVService readCSVService;

    InfluxDB influxDB;


    @PostConstruct
    public void init() {
        OkHttpClient.Builder client = new OkHttpClient.Builder()
                .readTimeout(2000, TimeUnit.SECONDS).pingInterval(30, TimeUnit.SECONDS);
        influxDB = InfluxDBFactory.connect(influxDBConfig.url, influxDBConfig.user, influxDBConfig.password, client);
        influxDB.setDatabase(influxDBConfig.dataBaseName);

        log.info("init");

        // influxToInflux();
        // allSubSysttemDataToCSV();
    }

    public List<Double> queryValues(String tagCode, Long subSystemId, String dateStrBegin, String dateStrEnd) {
        // 所有值
        List<Double> values = new ArrayList<>();

        try {
            SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.sss'Z'");
            sdf1.setTimeZone(TimeZone.getTimeZone("UTC"));

            SimpleDateFormat sdf2 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            sdf2.setTimeZone(TimeZone.getTimeZone("UTC"));

            String sql = sql = String.format("select Value as value from RealTimeData_%d where TagName = '%s' and time >= '%s' and time <= '%S' and Valid=True order by time", subSystemId, tagCode, dateStrBegin, dateStrEnd);

            List<QueryResult.Result> results = null;
            log.info("sql:" + sql);
            results = influxDB.query(new Query(sql)).getResults();

            if (results != null && results.get(0).getSeries() != null) {
                List<QueryResult.Series> seriesL = results.get(0).getSeries();
                // dateStr = String.valueOf(seriesL.get(0).getValues().get(seriesL.get(0).getValues().size() - 1).get(0));

                if (seriesL.size() > 0) {
                    QueryResult.Series series = seriesL.get(0);
                    for (List<Object> row : seriesL.get(0).getValues()) {
                        // String tagName = (String) row.get(1);
                        Double value = (Double) row.get(1);
                        // String time = (String) row.get(0);
                        values.add(value);
                    }

                }

            }

            log.info("values size: "+ String.valueOf(values.size()));

        }catch (Exception e) {
            e.printStackTrace();

        } finally {

            // influxDB.close();
        }
        return values;
    }

    public String beforeXMinutes(String dateStr) {
        SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.sss'Z'");
        sdf1.setTimeZone(TimeZone.getTimeZone("UTC"));

        SimpleDateFormat sdf2 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        sdf2.setTimeZone(TimeZone.getTimeZone("UTC"));
        try {
            SimpleDateFormat sdf = dateStr.length() > 20 ? sdf1 : sdf2;
            Date utilDate = sdf.parse(dateStr);
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(utilDate);
            calendar.add(Calendar.MINUTE, -30);

            Date afterTime = calendar.getTime();
            return sdf.format(afterTime);
        } catch (ParseException e) {
            e.printStackTrace();
            return "";
        }
    }

    // 往后加X分钟
    public String afterXMinutes(String dateStr) {
        SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.sss'Z'");
        sdf1.setTimeZone(TimeZone.getTimeZone("UTC"));

        SimpleDateFormat sdf2 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        sdf2.setTimeZone(TimeZone.getTimeZone("UTC"));
        try {
            SimpleDateFormat sdf = dateStr.length() > 20 ? sdf1 : sdf2;
            Date utilDate = sdf.parse(dateStr);
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(utilDate);
            calendar.add(Calendar.MINUTE, influxDBConfig.minutes);

            Date afterTime = calendar.getTime();
            return sdf.format(afterTime);
        } catch (ParseException e) {
            e.printStackTrace();
            return "";
        }
    }

    // 判断时间是否为已经结束
    public Boolean hasMore(String dateStr) {
        SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.sss'Z'");
        sdf1.setTimeZone(TimeZone.getTimeZone("UTC"));

        SimpleDateFormat sdf2 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        sdf2.setTimeZone(TimeZone.getTimeZone("UTC"));
        try {
            SimpleDateFormat sdf = dateStr.length() > 20 ? sdf1 : sdf2;
            Date sqlDate = sdf.parse(dateStr);
            long targetTimeMillis = sqlDate.getTime();
            long currentTimeMillis = System.currentTimeMillis();
            // 判断sql的时间与当前时间
            if (targetTimeMillis > currentTimeMillis) {
                return false;
            }else {
                // 24小时内，也认为结束
                return (currentTimeMillis - targetTimeMillis) > 32400000L;
            }
        } catch (ParseException e) {
            e.printStackTrace();
            return true;
        }
    }

    // 所有RealTimeData_子系统的数据导入到csv
    public void allSubSysttemDataToCSV() {

        // 读取csv
        Map<String, String> pointsMap = readCSVService.readCSV();
        // 实际值
        Map<String, InfluxdbValue> valuesRealtimeMin = new HashMap<>();
        Map<String, Double> valuesRealtimeAvg = new HashMap<>();
        Map<String, InfluxdbValue> valuesRealtimeMax = new HashMap<>();
        //
        Map<String, Double> valuesRealtimeZeroPercent = new HashMap<>();
        Map<String, Double> valuesRealtimeOnePercent = new HashMap<>();

        // 估计值
        Map<String, Double> valuesEstimateMin = new HashMap<>();
        Map<String, Double> valuesEstimateAvg = new HashMap<>();
        Map<String, Double> valuesEstimateMax = new HashMap<>();

        // 严重度
        Map<String, Double> valuesSeverityMin = new HashMap<>();
        Map<String, Double> valuesSeverityAvg = new HashMap<>();
        Map<String, Double> valuesSeverityMax = new HashMap<>();

        // XX菱形
        Map<String, int[]> valuesXX = new HashMap<>();

        Map<String, List<InfluxdbValue>> realTimeMap = getData(0);
        Map<String, List<InfluxdbValue>> estimateMap = getData(1);

        // 严重度值
        Map<String, List<InfluxdbValue>> severityMap = getData(2);
        // XX菱形
        Map<String, List<InfluxdbValue>> xxMap = getData(3);

        // 实际值
        for(Map.Entry<String, List<InfluxdbValue>>entry: realTimeMap.entrySet()) {
            String key = entry.getKey();
            List<InfluxdbValue> list = entry.getValue();
            if (list.size() == 0) {
                continue;
            }
            if (!(pointsMap.containsKey(key))) {
                continue;
            }
            if ((pointsMap.get(key) != null) && (!(pointsMap.get(key).equals("开关量")))) {
                //DoubleSummaryStatistics stats = list.stream().mapToDouble(Double::doubleValue).summaryStatistics();
//                // 最小值
//                valuesRealtimeMin.put(key, stats.getMin());
//                // 最大值
//                valuesRealtimeMax.put(key, stats.getMax());
                // 平均值
                // valuesRealtimeAvg.put(key, stats.getAverage());
                Double minValue = null;
                String minTime = null;
                Double maxValue = null;
                String maxTime = null;
                Double sumValue = 0.0;
                for (int i = 0; i < list.size(); i++) {
                    InfluxdbValue itemValue = list.get(i);
                    sumValue += itemValue.getValue();
                    if (i == 0) {
                        minValue = itemValue.getValue();
                        maxValue = itemValue.getValue();
                        minTime = itemValue.getTime();
                        maxTime = itemValue.getTime();
                    }else {
                        if (itemValue.getValue() < minValue) {
                            minValue = itemValue.getValue();
                            minTime = itemValue.getTime();
                        }else if (itemValue.getValue() > maxValue) {
                            maxValue = itemValue.getValue();
                            maxTime = itemValue.getTime();
                        }
                    }
                }
                InfluxdbValue influxdbValueMin = new InfluxdbValue();
                influxdbValueMin.setValue(minValue);
                influxdbValueMin.setTime(minTime);
                valuesRealtimeMin.put(key, influxdbValueMin);

                InfluxdbValue influxdbValueMax = new InfluxdbValue();
                influxdbValueMax.setValue(maxValue);
                influxdbValueMax.setTime(maxTime);
                valuesRealtimeMax.put(key, influxdbValueMax);

                valuesRealtimeAvg.put(key, sumValue / list.size());

            } else {
                int zeroCount = 0;
                for (int i = 0; i < list.size(); i++) {
                    InfluxdbValue itemValue = list.get(i);
                    if (Math.abs(itemValue.getValue()) < 0.0000001) {
                        zeroCount ++;
                    }
                }
                Double zeroPercent = Double.valueOf(zeroCount) / list.size();
                Double onePercent = Double.valueOf(list.size() - zeroCount) / list.size();
                valuesRealtimeZeroPercent.put(key, zeroPercent);
                valuesRealtimeOnePercent.put(key, onePercent);
            }

        }

        // 估计值
        for(Map.Entry<String, List<InfluxdbValue>>entry: estimateMap.entrySet()) {
            String key = entry.getKey();
            List<InfluxdbValue> list = entry.getValue();
            if (list.size() == 0) {
                continue;
            }
            // 估计值的最小值和最大值
            Double minValue = list.get(0).getValue();
            Double maxValue = list.get(0).getValue();

            if (valuesRealtimeMin.get(key) == null) {
                log.info("estimate:"+ key);
                continue;
            }
            String minTime = valuesRealtimeMin.get(key).getTime();
            String maxTime = valuesRealtimeMax.get(key).getTime();
            Boolean minTimeFound = false;
            Boolean maxTimeFound = false;

            Double sumValue = 0.0;
            for (int i = 0; i < list.size(); i++) {
                InfluxdbValue itemValue = list.get(i);
                sumValue += itemValue.getValue();
                if (!minTimeFound) {
                    if (itemValue.getTime().equals(minTime)) {
                        minValue = itemValue.getValue();
                        minTimeFound = true;
                    }
                }
                if (!maxTimeFound) {
                    if (itemValue.getTime().equals(maxTime)) {
                        maxValue = itemValue.getValue();
                        maxTimeFound = true;
                    }
                }
            }
            if (!minTimeFound) {
                log.warning(key+ "min");
            }
            if (!maxTimeFound) {
                log.warning(key+ "max");
            }


//            DoubleSummaryStatistics stats = list.stream().mapToDouble(Double::doubleValue).summaryStatistics();
//            // 最小值
            valuesEstimateMin.put(key, minValue);
            // 最大值
            valuesEstimateMax.put(key, maxValue);
            // 平均值
            valuesEstimateAvg.put(key, sumValue / list.size());
        }

        // 严重度
        for(Map.Entry<String, List<InfluxdbValue>>entry: severityMap.entrySet()) {
            String key = entry.getKey();
            List<InfluxdbValue> list = entry.getValue();
            if (list.size() == 0) {
                continue;
            }
            // 严重度的最小值和最大值
            Double minValue = list.get(0).getValue();
            Double maxValue = list.get(0).getValue();

            if (valuesRealtimeMin.get(key) == null) {
                // log.info("se:"+ key);
                continue;
            }
            String minTime = valuesRealtimeMin.get(key).getTime();
            String maxTime = valuesRealtimeMax.get(key).getTime();
            Boolean minTimeFound = false;
            Boolean maxTimeFound = false;

            Double sumValue = 0.0;
            for (int i = 0; i < list.size(); i++) {
                InfluxdbValue itemValue = list.get(i);
                sumValue += itemValue.getValue();
                if (!minTimeFound) {
                    if (itemValue.getTime().equals(minTime)) {
                        minValue = itemValue.getValue();
                        minTimeFound = true;
                    }
                }
                if (!maxTimeFound) {
                    if (itemValue.getTime().equals(maxTime)) {
                        maxValue = itemValue.getValue();
                        maxTimeFound = true;
                    }
                }
            }
            if (!minTimeFound) {
                log.warning(key+ "min");
            }
            if (!maxTimeFound) {
                log.warning(key+ "max");
            }

            valuesSeverityMin.put(key, minValue);
            // 最大值
            valuesSeverityMax.put(key, maxValue);
            // 平均值
            valuesSeverityAvg.put(key, sumValue / list.size());
        }

        // XX菱形
        for(Map.Entry<String, List<InfluxdbValue>>entry: xxMap.entrySet()) {
            String key = entry.getKey();
            List<InfluxdbValue> list = entry.getValue();
            if (list.size() == 0) {
                continue;
            }
            int smallCount = 0;
            int zeroCount = 0;
            int bigCount = 0;
            for (int i = 0; i < list.size(); i++) {
                InfluxdbValue itemValue = list.get(i);
                if (Math.abs(itemValue.getValue()) < 0.001) {
                    zeroCount += 1;
                }else if (Math.abs(itemValue.getValue() + 1.0) < 0.001) {
                    smallCount += 1;
                }else {
                    if (Math.abs(itemValue.getValue() - 1.0) < 0.0001) {
                        bigCount += 1;
                    }
                }
            }
            int[] xxCounts = new int[3];
            xxCounts[0] = smallCount;
            if (zeroCount > 31) {
                log.warning("xx:"+key+zeroCount);
            }
            xxCounts[1] = zeroCount;
            xxCounts[2] = bigCount;
            valuesXX.put(key, xxCounts);
        }

        List<UserCsvDTO> dataList = new ArrayList<>();
        // 写csv
        for(Map.Entry<String, List<InfluxdbValue>>entry: realTimeMap.entrySet()) {
            String tagCode = entry.getKey();

            UserCsvDTO userCsvDTO = new UserCsvDTO();
            userCsvDTO.setTagCode(tagCode);
            if (pointsMap.get(tagCode) == null) {
                log.info("not exist:" + tagCode);
                continue;
            }
            if ((pointsMap.get(tagCode) != null) && (!(pointsMap.get(tagCode).equals("开关量")))) {
                userCsvDTO.setRealMin(valuesRealtimeMin.get(tagCode).getValue());
                userCsvDTO.setRealMax(valuesRealtimeMax.get(tagCode).getValue());
                userCsvDTO.setRealAvg(valuesRealtimeAvg.get(tagCode));
            }else {
                userCsvDTO.setOnePercent(valuesRealtimeOnePercent.get(tagCode));
                userCsvDTO.setZeroPercent(valuesRealtimeZeroPercent.get(tagCode));
            }

            userCsvDTO.setEstimateMin(valuesEstimateMin.get(tagCode));
            userCsvDTO.setEstimateMax(valuesEstimateMax.get(tagCode));
            userCsvDTO.setEstimateAvg(valuesEstimateAvg.get(tagCode));
            if (valuesXX.get(tagCode) != null) {
                int[] xxValue = valuesXX.get(tagCode);
                userCsvDTO.setSmallCountXX(xxValue[0]);
                userCsvDTO.setNormalCountXX(xxValue[1]);
                userCsvDTO.setHighCountXx(xxValue[2]);
            }
            userCsvDTO.setSeverityMin(valuesSeverityMin.get(tagCode));
            userCsvDTO.setSeverityMax(valuesSeverityMax.get(tagCode));
            userCsvDTO.setSeverityAvg(valuesSeverityAvg.get(tagCode));
            dataList.add(userCsvDTO);
        }
        String filePath = "/Users/wangyouzhuo/Desktop/a/jingran/result/test.csv";
        EasyExcel.write(filePath, UserCsvDTO.class)
                .excelType(ExcelTypeEnum.CSV)
                .needHead(true)
                .sheet("机组测点数据")
                .doWrite(dataList);

        log.info("finish");
    }

    Map<String, List<InfluxdbValue>> getData(int type) {
        // 所有值
        Map<String, List<InfluxdbValue>> allValuesMap = new HashMap<>();

        String tableName = null;
        if (type == 0) {
            tableName = "RealTimeData";
        }else if (type == 1) {
            tableName = "Estimate";
        }else if (type == 2) {
            tableName = "TagSeverity";
        }else if (type == 3) {
            tableName = "XX";
        }

        try {
            SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.sss'Z'");
            sdf1.setTimeZone(TimeZone.getTimeZone("UTC"));

            SimpleDateFormat sdf2 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            sdf2.setTimeZone(TimeZone.getTimeZone("UTC"));

            String sql = "";
            if (type == 0 || type == 1) {
                sql = String.format("select TagName as tagName,Value as value,time from %s where time >= '%s' and time <= '%S' and Valid=True", tableName, influxDBConfig.starttime, influxDBConfig.endtime);
            }else if (type == 2){
                sql = String.format("select TagName as tagName,Severity as value,time from %s where time >= '%s' and time <= '%S' and Valid=True order by time", tableName, influxDBConfig.starttime, influxDBConfig.endtime);
            } else {
                sql = String.format("select TagName as tagName,Value as value,time from %s where time >= '%s' and time <= '%S' ", tableName, influxDBConfig.starttime, influxDBConfig.endtime);
            }

            List<QueryResult.Result> results = null;
            // log.info("sql:" + sql);
            results = influxDB.query(new Query(sql)).getResults();

            if (results != null && results.get(0).getSeries() != null) {
                List<QueryResult.Series> seriesL = results.get(0).getSeries();
                // dateStr = String.valueOf(seriesL.get(0).getValues().get(seriesL.get(0).getValues().size() - 1).get(0));

                if (seriesL.size() > 0) {
                    QueryResult.Series series = seriesL.get(0);
                    for (List<Object> row : seriesL.get(0).getValues()) {
                        String tagName = (String) row.get(1);
                        Double value = (Double) row.get(2);
                        String time = (String) row.get(0);
                        if (allValuesMap.get(tagName) == null) {
                            List<InfluxdbValue> list = new ArrayList<>();
                            allValuesMap.put(tagName, list);
                        }

                        List<InfluxdbValue> list = allValuesMap.get(tagName);
                        InfluxdbValue itemValue = new InfluxdbValue();
                        itemValue.setValue(value);
                        itemValue.setTime(time);
                        list.add(itemValue);

                    }

                }

            }

            log.info("allValuesMap size: "+ String.valueOf(allValuesMap.size()));

        }catch (Exception e) {
            e.printStackTrace();

        } finally {

            // influxDB.close();
        }
        return allValuesMap;
    }

}
