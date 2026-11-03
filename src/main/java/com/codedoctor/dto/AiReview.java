package com.codedoctor.dto;

import java.util.List;

/** Structured answer requested from the model (see resources/schemas/review.json). */
public record AiReview(
        String summary,
        String severity,
        List<CodeIssue> issues,
        List<SourceFile> correctedFiles,
        String explanation,
        List<String> optimizations,
        List<String> testCases) {

    public AiReview {
        summary = summary == null ? "" : summary;
        severity = severity == null ? "NONE" : severity;
        issues = issues == null ? List.of() : issues;
        correctedFiles = correctedFiles == null ? List.of() : correctedFiles;
        explanation = explanation == null ? "" : explanation;
        optimizations = optimizations == null ? List.of() : optimizations;
        testCases = testCases == null ? List.of() : testCases;
    }

    public static AiReview unavailable(String reason) {
        return new AiReview("AI analysis unavailable. " + reason, "NONE", null, null, null, null, null);
    }
}
