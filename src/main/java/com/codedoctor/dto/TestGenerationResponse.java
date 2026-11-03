package com.codedoctor.dto;

import java.util.List;

public record TestGenerationResponse(String summary, List<SourceFile> testFiles, List<String> scenarios, String note) {
    public TestGenerationResponse {
        summary = summary == null ? "" : summary;
        testFiles = testFiles == null ? List.of() : testFiles;
        scenarios = scenarios == null ? List.of() : scenarios;
        note = note == null ? "" : note;
    }
}
