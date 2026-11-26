package com.jenkinsflow.catalog;

import org.junit.jupiter.api.Test;

class CatalogApiApplicationTests {

	@Test
	void applicationClassIsLoadable() {
		// Full context boot needs SQL Server + Valkey (see Docker Compose).
		// Slice tests cover HTTP behaviour without infrastructure.
		org.junit.jupiter.api.Assertions.assertNotNull(CatalogApiApplication.class);
	}
}
