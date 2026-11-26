package com.jenkinsflow.catalog.web.dto;

import com.jenkinsflow.catalog.domain.Book;
import java.io.Serializable;

public record BookResponse(
		Long id,
		String title,
		Long authorId,
		String authorName,
		Integer publishedYear
) implements Serializable {

	private static final long serialVersionUID = 1L;

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
