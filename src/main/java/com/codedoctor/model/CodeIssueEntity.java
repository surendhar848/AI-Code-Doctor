package com.codedoctor.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "code_issues")
public class CodeIssueEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id")
    private CodeReview review;

    @Column(name = "issue_type", length = 100)
    private String issueType;

    @Column(length = 20)
    private String severity;

    @Column(name = "line_number")
    private Integer lineNumber;

    @Column(length = 4000)
    private String description;

    @Column(length = 4000)
    private String suggestion;

    protected CodeIssueEntity() {}

    public CodeIssueEntity(String issueType, String severity, Integer lineNumber, String description, String suggestion) {
        this.issueType = issueType;
        this.severity = severity;
        this.lineNumber = lineNumber;
        this.description = description;
        this.suggestion = suggestion;
    }

    void setReview(CodeReview review) { this.review = review; }
}
