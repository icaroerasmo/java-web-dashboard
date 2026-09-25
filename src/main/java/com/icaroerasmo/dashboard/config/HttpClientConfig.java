package com.icaroerasmo.dashboard.config;

import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;

/**
 * Applies connection and read timeouts to every {@code RestClient.Builder}
 * built by Spring Boot. Without this, a module that hangs (e.g. while starting
 * up) would keep a Tomcat thread blocked indefinitely; the timeouts make such
 * calls fail fast and surface as a diagnosable 502.
 */
@Configuration
public class HttpClientConfig {

    @Bean
    public RestClientCustomizer restClientCustomizer() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(30));
        return builder -> builder.requestFactory(factory);
    }
}
