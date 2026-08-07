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

    @Value("${fastgpt.embeddingWeightV2:0.6}")
    Double embeddingWeightV2;

    @Value("${fastgpt.limit}")
    Integer limit;

    @Value("${fastgpt.limitV2:2000}")
    Integer limitV2;

    @Value("${fastgpt.similarity}")
    Double similarity;

    @Value("${fastgpt.similarityV2:0.63}")
    Double similarityV2;

    @Value("${fastgpt.datasetSearchExtensionModel}")
    String datasetSearchExtensionModel;
}
