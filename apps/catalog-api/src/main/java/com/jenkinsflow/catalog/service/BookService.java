package com.jenkinsflow.catalog.service;

import com.jenkinsflow.catalog.domain.Author;
import com.jenkinsflow.catalog.domain.Book;
import com.jenkinsflow.catalog.repository.AuthorRepository;
import com.jenkinsflow.catalog.repository.BookRepository;
import com.jenkinsflow.catalog.web.dto.BookResponse;
import com.jenkinsflow.catalog.web.dto.CreateBookRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BookService {

	private static final Logger log = LoggerFactory.getLogger(BookService.class);

	private final BookRepository bookRepository;
	private final AuthorRepository authorRepository;

	public BookService(BookRepository bookRepository, AuthorRepository authorRepository) {
		this.bookRepository = bookRepository;
		this.authorRepository = authorRepository;
	}

	@Transactional(readOnly = true)
	@Cacheable(cacheNames = "books", key = "'all'")
	public List<BookResponse> listBooks() {
		log.info("CACHE MISS books:all — loading from SQL Server");
		return bookRepository.findAllWithAuthor().stream().map(BookResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public BookResponse getBook(Long id) {
		return bookRepository.findById(id)
				.map(book -> {
					book.getAuthor().getName();
					return BookResponse.from(book);
				})
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found"));
	}

	@Transactional
	@CacheEvict(cacheNames = "books", allEntries = true)
	public BookResponse createBook(CreateBookRequest request) {
		Author author = authorRepository.findById(request.authorId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Author not found"));
		Book saved = bookRepository.save(new Book(request.title(), author, request.publishedYear()));
		log.info("Created book id={} and evicted books cache", saved.getId());
		return BookResponse.from(saved);
	}
}
