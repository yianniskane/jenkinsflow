package com.jenkinsflow.catalog.config;

import java.time.Duration;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableCaching
public class CacheConfig {

	@Bean
	RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer(ObjectMapper objectMapper) {
		var serializer = new GenericJacksonJsonRedisSerializer(objectMapper);
		return builder -> builder.withCacheConfiguration(
				"books",
				RedisCacheConfiguration.defaultCacheConfig()
						.entryTtl(Duration.ofMinutes(5))
						.disableCachingNullValues()
						.serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer))
		);
	}
}
