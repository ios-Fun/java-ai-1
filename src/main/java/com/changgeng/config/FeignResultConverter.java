package com.changgeng.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.formula.functions.T;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class FeignResultConverter {

    private final ObjectMapper objectMapper;

    public FeignResultConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 将 Feign 返回的 Object 安全转换为指定类型
     */
    public <T> T convert(Object rawResult, Class<T> targetType) {
        if (rawResult == null) {
            return null;
        }
        // 如果已经是目标类型（极少数情况），直接返回
        if (targetType.isInstance(rawResult)) {
            return targetType.cast(rawResult);
        }
        try {
            return objectMapper.convertValue(rawResult, targetType);
        } catch (IllegalArgumentException e) {
            log.error("Feign结果转换失败, targetType={}, rawType={}",
                    targetType.getName(), rawResult.getClass().getName(), e);
            throw new RuntimeException("接口返回数据结构与预期不符", e);
        }
    }

    /**
     * 带泛型的转换（如 List<XxxDTO>）
     */
    public <T> T convert(Object rawResult, TypeReference<T> typeRef) {
        if (rawResult == null) {
            return null;
        }
        try {
            return objectMapper.convertValue(rawResult, typeRef);
        } catch (IllegalArgumentException e) {
            log.error("Feign泛型结果转换失败, typeRef={}", typeRef.getType(), e);
            throw new RuntimeException("接口返回数据结构与预期不符", e);
        }
    }

    /**
     * 从 debug 或 unfold 的原始返回中提取 name + sourceId
     */
    public <T> List<T> extractNodeInfos(Object rawData, Class<T> targetType) {
        if (rawData == null) {
            return Collections.emptyList();
        }

        JsonNode node = objectMapper.valueToTree(rawData);

        // data 是数组 → unfold 场景
        if (node.isArray()) {
            // ✅ 用 TypeFactory 动态构造 List<targetType>，保留完整泛型信息
            JavaType listType = objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, targetType);
            return objectMapper.convertValue(node, listType);
        }

        // data 是对象 → debug 场景
        if (node.isObject()) {
            // ✅ 直接用传入的 Class<T>，而非 T.class
            T info = objectMapper.convertValue(node, targetType);
            return Collections.singletonList(info);
        }

        log.warn("无法识别的data结构: {}", node.getNodeType());
        return Collections.emptyList();
    }
}