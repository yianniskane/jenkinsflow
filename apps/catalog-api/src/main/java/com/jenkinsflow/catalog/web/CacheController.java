package com.jenkinsflow.catalog.web;

import com.jenkinsflow.catalog.web.dto.CacheStatsResponse;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cache")
public class CacheController {

	private final CacheManager cacheManager;

	public CacheController(CacheManager cacheManager) {
		this.cacheManager = cacheManager;
	}

	@GetMapping("/stats")
	public CacheStatsResponse stats() {
		Cache books = cacheManager.getCache("books");
		boolean present = books != null && books.get("all") != null;
		return new CacheStatsResponse(
				"books",
				present,
				present
						? "books:all is warm in Valkey — next GET /api/books should be a cache hit (no CACHE MISS log)"
						: "books:all is cold — next GET /api/books will load from SQL Server"
		);
	}

	@DeleteMapping("/books")
	public CacheStatsResponse clearBooks() {
		Cache books = cacheManager.getCache("books");
		if (books != null) {
			books.clear();
		}
		return new CacheStatsResponse("books", false, "Cleared books cache in Valkey");
	}
}
