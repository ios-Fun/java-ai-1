package com.changgeng.pojo;

import lombok.Data;

import java.util.List;

@Data
public class ApiResponse {
    private Integer code;
    private Object data;
    private Boolean success;
    private String message;
}
