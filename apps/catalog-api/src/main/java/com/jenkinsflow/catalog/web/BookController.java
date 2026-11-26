package com.jenkinsflow.catalog.web;

import com.jenkinsflow.catalog.service.BookService;
import com.jenkinsflow.catalog.web.dto.BookResponse;
import com.jenkinsflow.catalog.web.dto.CreateBookRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/books")
public class BookController {

	private final BookService bookService;

	public BookController(BookService bookService) {
		this.bookService = bookService;
	}

	@GetMapping
	public List<BookResponse> list() {
		return bookService.listBooks();
	}

	@GetMapping("/{id}")
	public BookResponse get(@PathVariable Long id) {
		return bookService.getBook(id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public BookResponse create(@Valid @RequestBody CreateBookRequest request) {
		return bookService.createBook(request);
	}
}
