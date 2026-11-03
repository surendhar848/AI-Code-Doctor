package com.codedoctor.dto;

import jakarta.validation.constraints.NotBlank;

public record SourceFile(String name, @NotBlank(message = "source code must not be empty") String content) {}
