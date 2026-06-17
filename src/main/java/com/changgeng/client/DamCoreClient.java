package com.changgeng.client;

import com.changgeng.pojo.IdObj;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(name="dam-core", url = "${dam-core.url}")
public interface DamCoreClient {
    @PostMapping("/fault/graph/debut")
    Map debut(@RequestBody IdObj param);

    @PostMapping("/fault/graph/unfold")
    Map unfold(@RequestBody IdObj param);

    @PostMapping("/api/v1/dam/instance/selectAllUnit")
    Map selectAllUnit();
}
