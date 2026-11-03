package com.codedoctor.dto;

import java.util.List;

public record CodeReviewResponse(
        Long reviewId,
        String title,
        String language,
        AnalysisMode mode,
        List<SourceFile> originalFiles,
        CompilerResult originalCompilation,
        AiReview ai,
        String aiError,
        List<SourceFile> correctedFiles,
        CompilerResult correctedCompilation,
        boolean correctionVerified,
        int fixAttempts,
        String disclaimer) {

    public CodeReviewResponse withReviewId(Long id) {
        return new CodeReviewResponse(id, title, language, mode, originalFiles, originalCompilation, ai, aiError,
                correctedFiles, correctedCompilation, correctionVerified, fixAttempts, disclaimer);
    }
}
