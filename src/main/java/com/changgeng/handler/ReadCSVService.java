package com.changgeng.handler;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.read.listener.PageReadListener;
import com.changgeng.pojo.PointData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author wangyouzhuo
 * @Date 2026/5/7 17:26
 * @Version 1.0
 */

@Component
@Slf4j
public class ReadCSVService {

    @Value("${csv.fileName:}")
    public String fileName;

    // @PostConstruct
    public Map<String, String> readCSV() {
        Map<String, String> pointsMap = new HashMap<>();
        List<PointData> list = new ArrayList<>();
        // String fileName
        EasyExcel.read(fileName, PointData.class, new PageReadListener<PointData>(dataList -> {
            for (PointData pointData : dataList) {
                // log.info("读取到一条数据{}", pointData.toString());
                //
                list.add(pointData);
            }
        })).sheet().headRowNumber(1).doRead();
        log.info("list: {}", list.size());
        for (int i = 0; i < list.size();i++) {
            PointData pointData = list.get(i);
            if (pointData.getCode() == null) {
                continue;
            }
            if (pointData.getCode().equals("GLND_SEAL_STM_TEMP_1")) {
                log.info("bbb");
            }
            pointsMap.put(pointData.getCode(), pointData.getUnit());
        }
        log.info("map: {}", pointsMap.size());
        return pointsMap;
    }
}
