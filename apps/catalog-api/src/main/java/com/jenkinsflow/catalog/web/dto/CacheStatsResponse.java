package com.jenkinsflow.catalog.web.dto;

public record CacheStatsResponse(
		String cacheName,
		boolean present,
		String note
) {
}
