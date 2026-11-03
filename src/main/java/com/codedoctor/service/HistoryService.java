package com.codedoctor.service;

import com.codedoctor.dto.CodeIssue;
import com.codedoctor.dto.CodeReviewResponse;
import com.codedoctor.dto.ReviewSummary;
import com.codedoctor.dto.SourceFile;
import com.codedoctor.exception.ReviewNotFoundException;
import com.codedoctor.model.CodeIssueEntity;
import com.codedoctor.model.CodeReview;
import com.codedoctor.repository.ChatMessageRepository;
import com.codedoctor.repository.CodeReviewRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HistoryService {

    private static final int PAGE_SIZE = 20;

    private final CodeReviewRepository reviews;
    private final ChatMessageRepository chats;
    private final ObjectMapper mapper;

    public HistoryService(CodeReviewRepository reviews, ChatMessageRepository chats, ObjectMapper mapper) {
        this.reviews = reviews;
        this.chats = chats;
        this.mapper = mapper;
    }

    @Transactional
    public CodeReviewResponse save(CodeReviewResponse r) {
        String status = r.aiError() == null ? "SUCCESS" : "FAILED";
        CodeReview entity = new CodeReview(
                r.title(), r.language(), r.mode().name(), status, r.ai().severity(),
                truncate(r.ai().summary(), 1000), join(r.originalFiles()), join(r.correctedFiles()));
        for (CodeIssue i : r.ai().issues()) {
            entity.addIssue(new CodeIssueEntity(truncate(i.type(), 100), truncate(i.severity(), 20), i.line(),
                    truncate(i.description(), 4000), truncate(i.suggestion(), 4000)));
        }
        reviews.save(entity);
        CodeReviewResponse withId = r.withReviewId(entity.getId());
        entity.setResponseJson(toJson(withId));
        return withId;
    }

    @Transactional(readOnly = true)
    public List<ReviewSummary> list(String query, int page) {
        PageRequest pr = PageRequest.of(Math.max(page, 0), PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<CodeReview> result = query == null || query.isBlank()
                ? reviews.findAll(pr)
                : reviews.search(query.trim(), pr);
        return result.stream()
                .map(r -> new ReviewSummary(r.getId(), r.getTitle(), r.getLanguage(), r.getMode(),
                        r.getSeverity(), r.getSummary(), r.getStatus(), r.getCreatedAt()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CodeReviewResponse get(Long id) {
        CodeReview entity = reviews.findById(id).orElseThrow(() -> new ReviewNotFoundException(id));
        try {
            return mapper.readValue(entity.getResponseJson(), CodeReviewResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored review " + id + " could not be read", e);
        }
    }

    @Transactional
    public void delete(Long id) {
        if (!reviews.existsById(id)) {
            throw new ReviewNotFoundException(id);
        }
        chats.deleteByReviewId(id);
        reviews.deleteById(id);
    }

    /** Markdown report for download. */
    public String report(Long id) {
        CodeReviewResponse r = get(id);
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(r.title()).append("\n\n");
        sb.append("- Mode: ").append(r.mode()).append("\n- Severity: ").append(r.ai().severity()).append("\n\n");
        sb.append("## Summary\n").append(r.ai().summary()).append("\n\n");
        if (!r.ai().explanation().isBlank()) {
            sb.append("## Explanation\n").append(r.ai().explanation()).append("\n\n");
        }
        sb.append("## Compiler output (original)\n```\n").append(r.originalCompilation().asText()).append("```\n\n");
        if (!r.ai().issues().isEmpty()) {
            sb.append("## Findings (AI suggestions - verify before applying)\n");
            for (CodeIssue i : r.ai().issues()) {
                sb.append("- **").append(i.severity()).append("** ").append(i.type());
                if (i.line() != null && i.line() > 0) sb.append(" (line ").append(i.line()).append(")");
                sb.append(": ").append(i.description()).append(" Suggestion: ").append(i.suggestion()).append("\n");
            }
            sb.append("\n");
        }
        if (!r.correctedFiles().isEmpty()) {
            sb.append("## Corrected code (").append(r.correctionVerified() ? "compiles" : "NOT verified").append(")\n");
            for (SourceFile f : r.correctedFiles()) {
                sb.append("### ").append(f.name()).append("\n```java\n").append(f.content()).append("\n```\n\n");
            }
        }
        return sb.toString();
    }

    private String join(List<SourceFile> files) {
        return files.stream().map(f -> "// ===== " + f.name() + " =====\n" + f.content()).collect(Collectors.joining("\n\n"));
    }

    private String toJson(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise review", e);
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
