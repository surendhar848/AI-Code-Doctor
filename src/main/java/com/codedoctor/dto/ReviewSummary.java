package com.codedoctor.dto;

import java.time.Instant;

public record ReviewSummary(Long id, String title, String language, String mode, String severity,
                            String summary, String status, Instant createdAt) {}
