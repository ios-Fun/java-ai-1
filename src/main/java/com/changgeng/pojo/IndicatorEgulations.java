package com.changgeng.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class IndicatorEgulations {

    private Integer id;

    private String name;

    private String newLimit;

    private String limit;

    private String importantLimit;
}
