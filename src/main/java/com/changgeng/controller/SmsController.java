package com.changgeng.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/sms")
public class SmsController {

    @RequestMapping("/send")
    public Map<String, Object> sendSM() {
        Map<String, Object> map = new HashMap<>(3);
        map.put("code", "200");
        map.put("message", "操作成功");
        map.put("success", true);
        return map;
    }
}
