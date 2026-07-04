package com.changgeng.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@FeignClient(name="dam-ext", url = "${dam-ext.url}")
public interface DamExtClient {
    @PostMapping("/graph/tags")
    List<Map> getTags(@RequestParam Integer nodeId);

    @PostMapping("/graph/unitsOrAssets")
    List<Map<String, Object>> getUnitsOrAssetsProps(@RequestParam(required = false) String assetName,
                                                                     @RequestParam(required = false) String unitName);
    @PostMapping("/graph/getItems")
    List<Map> getItems(@RequestParam Integer unitId, @RequestParam String type);
}
