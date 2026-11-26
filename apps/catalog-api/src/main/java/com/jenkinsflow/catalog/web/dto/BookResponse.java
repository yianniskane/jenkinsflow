package com.jenkinsflow.catalog.web.dto;

import com.jenkinsflow.catalog.domain.Book;

public record BookResponse(
		Long id,
		String title,
		Long authorId,
		String authorName,
		Integer publishedYear
) {

	public static BookResponse from(Book book) {
		return new BookResponse(
				book.getId(),
				book.getTitle(),
				book.getAuthor().getId(),
				book.getAuthor().getName(),
				book.getPublishedYear()
		);
	}
}
