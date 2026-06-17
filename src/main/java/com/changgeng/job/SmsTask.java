package com.changgeng.job;

// import com.changgeng.handler.InfluxDBService;
import com.changgeng.handler.InfluxDBServiceJR;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;

// 定时器作用：如果端口被占用时，可以定时器不断的去注册
@Service
public class SmsTask {
    private static final Logger logger = LoggerFactory.getLogger(SmsTask.class);
    @Autowired
    private InfluxDBServiceJR influxDBService;
    private static boolean overPort = false;

    @PostConstruct
    public void sms() {
        if (!overPort) {
            influxDBService.init();
            overPort = true;
        }
    }
}
