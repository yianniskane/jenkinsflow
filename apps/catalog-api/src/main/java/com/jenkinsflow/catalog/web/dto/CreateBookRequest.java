package com.jenkinsflow.catalog.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateBookRequest(
		@NotBlank @Size(max = 255) String title,
		@NotNull Long authorId,
		@NotNull @Min(1000) @Max(2100) Integer publishedYear
) {
}
