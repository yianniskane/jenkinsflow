package com.jenkinsflow.catalog.web;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthDemoController {

	@GetMapping("/health-demo")
	public Map<String, String> healthDemo() {
		return Map.of(
				"status", "UP",
				"service", "catalog-api",
				"message", "Learning lab API is reachable"
		);
	}
}
