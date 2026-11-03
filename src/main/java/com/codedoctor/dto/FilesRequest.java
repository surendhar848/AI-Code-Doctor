package com.codedoctor.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record FilesRequest(String language, @NotEmpty @Valid List<SourceFile> files) {}
