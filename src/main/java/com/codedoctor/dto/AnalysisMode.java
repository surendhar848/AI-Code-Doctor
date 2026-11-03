package com.codedoctor.dto;

/** Each mode maps to its own prompt file in resources/prompts. */
public enum AnalysisMode {
    DEBUG("debug-prompt"),
    REVIEW("review-prompt"),
    SECURITY("security-prompt"),
    OPTIMIZE("optimize-prompt"),
    EXPLAIN("explain-prompt");

    private final String promptName;
    AnalysisMode(String promptName) { this.promptName = promptName; }
    public String promptName() { return promptName; }
}
