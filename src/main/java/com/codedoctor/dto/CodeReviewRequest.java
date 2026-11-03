package com.codedoctor.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CodeReviewRequest(
        @Size(max = 200) String title,
        String language,
        @NotEmpty(message = "submit at least one file") @Valid List<SourceFile> files,
        AnalysisMode mode,
        @Size(max = 10000) String errorText,
        @Size(max = 2000) String question,
        Boolean save) {}
