package com.changgeng.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name="dam-sdk", url = "${dam-sdk.url}")
public interface DamSdkClient {
    @PostMapping("/api/v1/dam/instance/getAlarmRef")
    Map getAlarmRef(@RequestBody Map<String, Object> map);
}
