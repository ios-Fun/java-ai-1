package com.changgeng.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class InfluxDBConfig {
    @Value("${influxdb.url}")
    public String url;

    @Value("${influxdb.user}")
    public String user;

    @Value("${influxdb.password}")
    public String password;

    @Value("${influxdb.dataBaseName}")
    public String dataBaseName;

    @Value("${influxdb.starttime}")
    public String starttime;

    @Value("${influxdb.endtime}")
    public String endtime;

//    @Value("${influxdb.tableName}")
//    public String tableName;

//    @Value("${influxdb.preBeginDate}")
//    public String preBeginDate;
//
//    @Value("${influxdb.beginDate}")
//    public String beginDate;
//
//    @Value("${influxdb.endDate}")
//    public String endDate;

//     @Value("${influxdb.count}")
//     public Integer count;

    @Value("${influxdb.minutes:20}")
    public Integer minutes;

    // 0--如果数据来源是是RealTimeData一个表
    // 1--如果数据来源是RealTimeData_*****多个表
//    @Value("${influxdb.type:1}")
//    public int type;

//    @Value("${influxdb.systemIdMin:100}")
//    public int systemIdMin;
//
//    @Value("${influxdb.systemIdMax:20000}")
//    public int systemIdMax;
}
