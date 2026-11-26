package com.jenkinsflow.catalog.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jenkinsflow.catalog.service.BookService;
import com.jenkinsflow.catalog.web.dto.BookResponse;
import com.jenkinsflow.catalog.web.dto.CreateBookRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = BookController.class)
class BookControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private BookService bookService;

	@Test
	void listBooksReturnsPayload() throws Exception {
		when(bookService.listBooks()).thenReturn(List.of(
				new BookResponse(1L, "Notes on the Analytical Engine", 1L, "Ada Lovelace", 1843)
		));

		mockMvc.perform(get("/api/books"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].title").value("Notes on the Analytical Engine"))
				.andExpect(jsonPath("$[0].authorName").value("Ada Lovelace"));
	}

	@Test
	void createBookReturnsCreated() throws Exception {
		when(bookService.createBook(any(CreateBookRequest.class)))
				.thenReturn(new BookResponse(3L, "New Book", 1L, "Ada Lovelace", 2024));

		mockMvc.perform(post("/api/books")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"title":"New Book","authorId":1,"publishedYear":2024}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(3));
	}
}
