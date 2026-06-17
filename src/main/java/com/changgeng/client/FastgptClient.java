package com.changgeng.client;

import feign.Headers;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.Map;

@FeignClient(name = "fastgpt", path = "/api", url = "${fastgpt.url}")
public interface FastgptClient {
    // @Headers({"Content-Type: application/json;charset=UTF-8", "Authorization=Bearer ${fastgpt.token}"})
    @PostMapping(value = "/core/dataset/searchTest", headers = {"Content-Type=application/json;charset=UTF-8", "Authorization=Bearer ${fastgpt.token}"})
    Map searchTest(Map params);
}
