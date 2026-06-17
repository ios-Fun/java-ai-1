package com.changgeng.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Data
public class RagConfig {
    @Value("${fastgpt.datasetId}")
    String datasetId;

    @Value("${fastgpt.embeddingWeight}")
    Double embeddingWeight;

    @Value("${fastgpt.limit}")
    Integer limit;

    @Value("${fastgpt.similarity}")
    Double similarity;

    @Value("${fastgpt.datasetSearchExtensionModel}")
    String datasetSearchExtensionModel;
}
