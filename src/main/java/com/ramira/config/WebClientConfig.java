package com.ramira.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Shared WebClient bean used for calling the Telegram Bot API.
 * WebClient is non-blocking, which complements our async notification service.
 */
@Configuration
public class WebClientConfig {

	 @Bean
	    public WebClient webClient() {
	        return WebClient.builder()
	                .codecs(configurer ->
	                        configurer.defaultCodecs()
	                                .maxInMemorySize(1024 * 1024))
	                .build();
	    }
}
