package com.changgeng.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class InfluxQueryResult {
    private List<String> timestamps; // 时间戳列表
    private List<Double> actualValues;     // 真实值列表
    private List<Double> estimateValues;     // 真实值列表
    private List<Double> severity;     // 真实值列表

    public InfluxQueryResult(List<String> timestamps, List<Double> actualValues) {
        this.timestamps = timestamps;
        this.actualValues = actualValues;
    }
}