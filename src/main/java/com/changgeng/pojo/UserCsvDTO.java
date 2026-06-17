package com.changgeng.pojo;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * @Author wangyouzhuo
 * @Date 2026/4/28 14:56
 * @Version 1.0
 */
@Data
public class UserCsvDTO {

    @ExcelProperty(value = "测点编码",index=0)
    private String tagCode;

    @ExcelProperty(value = "实际值最小值",index=1)
    private Double realMin;

    @ExcelProperty(value = "实际值最大值",index=2)
    private Double realMax;

    @ExcelProperty(value = "实际值平均值",index=3)
    private Double realAvg;

    @ExcelProperty(value = "开关量0值占比",index=4)
    private Double zeroPercent;

    @ExcelProperty(value = "开关量1值占比",index=5)
    private Double onePercent;

    @ExcelProperty(value = "估计值最小值",index=6)
    private Double estimateMin;

    @ExcelProperty(value = "估计值最大值",index=7)
    private Double estimateMax;

    @ExcelProperty(value = "估计值平均值",index=8)
    private Double estimateAvg;

    @ExcelProperty(value = "超限偏低个数",index=9)
    private Integer smallCountXX;

    @ExcelProperty(value = "超限正常个数",index=10)
    private Integer normalCountXX;

    @ExcelProperty(value = "超限偏高个数",index=11)
    private Integer highCountXx;

    @ExcelProperty(value = "严重度最小值",index=12)
    private Double severityMin;

    @ExcelProperty(value = "严重度最大值",index=13)
    private Double severityMax;

    @ExcelProperty(value = "严重度平均值",index=14)
    private Double severityAvg;
}
