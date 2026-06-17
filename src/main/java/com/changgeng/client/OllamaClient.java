package com.changgeng.client;

import com.changgeng.prompt.pojo.LLMResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
@FeignClient(name = "ollama", path = "/api", url = "${ollama.url}")
public interface OllamaClient {
    @PostMapping(value = {"/generate"}, consumes = {"application/json"})
    LLMResult generate(Object params);
}