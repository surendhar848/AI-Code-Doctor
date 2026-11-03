package com.codedoctor.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "code_reviews")
public class CodeReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "review_title", length = 200, nullable = false)
    private String title;

    @Column(length = 30, nullable = false)
    private String language;

    @Column(length = 20, nullable = false)
    private String mode;

    @Column(length = 20, nullable = false)
    private String status;

    @Column(length = 20)
    private String severity;

    @Column(length = 1000)
    private String summary;

    @Lob
    @Column(name = "original_code")
    private String originalCode;

    @Lob
    @Column(name = "corrected_code")
    private String correctedCode;

    /** The complete CodeReviewResponse as JSON, so a saved review can be re-opened exactly as it was. */
    @Lob
    @Column(name = "ai_response")
    private String responseJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CodeIssueEntity> issues = new ArrayList<>();

    protected CodeReview() {}

    public CodeReview(String title, String language, String mode, String status, String severity,
                      String summary, String originalCode, String correctedCode) {
        this.title = title;
        this.language = language;
        this.mode = mode;
        this.status = status;
        this.severity = severity;
        this.summary = summary;
        this.originalCode = originalCode;
        this.correctedCode = correctedCode;
    }

    public void addIssue(CodeIssueEntity issue) {
        issue.setReview(this);
        issues.add(issue);
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getLanguage() { return language; }
    public String getMode() { return mode; }
    public String getStatus() { return status; }
    public String getSeverity() { return severity; }
    public String getSummary() { return summary; }
    public String getResponseJson() { return responseJson; }
    public void setResponseJson(String responseJson) { this.responseJson = responseJson; }
    public Instant getCreatedAt() { return createdAt; }
}
