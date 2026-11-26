package com.jenkinsflow.catalog.config;

import java.time.Duration;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;

@Configuration
@EnableCaching
public class CacheConfig {

	/**
	 * JDK serialization is used on purpose for this lab: Jackson JSON without type
	 * metadata fails on the second {@code GET /api/books} when reading {@code List}
	 * values back from Valkey (500). Serializable DTOs keep cache hits reliable.
	 */
	@Bean
	RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
		return builder -> builder.withCacheConfiguration(
				"books",
				RedisCacheConfiguration.defaultCacheConfig()
						.entryTtl(Duration.ofMinutes(5))
						.disableCachingNullValues()
						.serializeValuesWith(RedisSerializationContext.SerializationPair
								.fromSerializer(RedisSerializer.java()))
		);
	}
}
