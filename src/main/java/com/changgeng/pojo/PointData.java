package com.changgeng.pojo;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * @Author wangyouzhuo
 * @Date 2026/5/7 17:21
 * @Version 1.0
 */
@Data
public class PointData {
    @ExcelProperty(index = 1)
    private String code;

    @ExcelProperty(index = 4)
    private String unit;

//    @ExcelProperty(index = 4)
//    private String code;
//
//    @ExcelProperty(index = 6)
//    private String type;
}
