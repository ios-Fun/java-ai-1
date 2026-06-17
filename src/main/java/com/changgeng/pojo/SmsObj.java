package com.changgeng.pojo;

import lombok.Data;

import java.io.Serializable;

@Data
public class SmsObj implements Serializable {
    private static final long serialVersionUID = 1L;
    private String phone;   // 手机号
    private String context;// 内容
}
