package com.codedoctor.controller;

import com.codedoctor.dto.CodeReviewResponse;
import com.codedoctor.dto.ReviewSummary;
import com.codedoctor.service.HistoryService;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/history")
public class HistoryController {

    private final HistoryService history;

    public HistoryController(HistoryService history) {
        this.history = history;
    }

    @GetMapping
    public List<ReviewSummary> list(@RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page) {
        return history.list(q, page);
    }

    @GetMapping("/{id}")
    public CodeReviewResponse get(@PathVariable Long id) {
        return history.get(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        history.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/report")
    public ResponseEntity<String> report(@PathVariable Long id) {
        String markdown = history.report(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("review-" + id + ".md").build().toString())
                .contentType(MediaType.parseMediaType("text/markdown;charset=UTF-8"))
                .body(markdown);
    }
}
