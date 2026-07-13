package com.changgeng.controller;

import com.changgeng.client.DamExtClient;
import com.changgeng.common.result.Result;
import com.changgeng.tool.CommonTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.apache.commons.collections4.CollectionUtils.collect;

@RestController
@RequestMapping("/ai/common")
@Slf4j
public class CommonController {

    List<Map> instanceList;

    @Autowired
    DamExtClient damExtClient;

    /**
     * 根据输入的匹配字符串，从所有实例中模糊匹配出相似度最高的前10个实例。
     * 匹配逻辑：
     * 1. 通过远程服务(dam-ext)获取全量实例列表；
     * 2. 对类型为"开关量"或"模拟量"的实例，将其类型统一加上"测点-"前缀；
     * 3. 基于混合杰卡德相似度（字符级 + 词级 + 基础杰卡德）对实例名称与输入字符串进行相似度计算；
     * 4. 按相似度降序排序，返回前10个最匹配的结果。
     * @param matchString 用户输入的匹配字符串
     * @return 相似度最高的前10个实例信息（包含id、name、code、type、similarity）
     */
    @RequestMapping("/matchForBest")
    public Result matchForBest(@RequestParam String matchString) {
        instanceList = damExtClient.getInstanceList().stream()
                .map(o -> {
                    String type = (String) o.get("type");
                    if ("开关量".equals(type) || "模拟量".equals(type)) o.put("type", "测点-" + type);
                    return o;
                })
                .collect(Collectors.toList());
        List<Map> matchedStr =  CommonTool.getBestMatchingStr(instanceList,matchString, 10);
        log.info("matchedResult: {}", matchedStr);
        return Result.success(matchedStr);
    }

}
