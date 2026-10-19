package com.railway.simulator.controller.dto;

import java.util.List;

public class AlgorithmMetadataDto {

    public record AlgorithmInfo(
            String name,
            String dataStructure,
            String purpose,
            String timeComplexity,
            String spaceComplexity,
            String whereUsed,
            List<String> interviewKeyPoints
    ) {}

    private List<AlgorithmInfo> algorithms;

    public AlgorithmMetadataDto() {}

    public AlgorithmMetadataDto(List<AlgorithmInfo> algorithms) {
        this.algorithms = algorithms;
    }

    public List<AlgorithmInfo> getAlgorithms() {
        return algorithms;
    }

    public void setAlgorithms(List<AlgorithmInfo> algorithms) {
        this.algorithms = algorithms;
    }
}
