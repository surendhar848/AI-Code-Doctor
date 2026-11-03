package com.codedoctor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(Long reviewId, @NotBlank @Size(max = 4000) String message) {}
