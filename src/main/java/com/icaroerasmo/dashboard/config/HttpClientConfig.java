package com.icaroerasmo.dashboard.config;

import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Applies connection and read timeouts to every {@code RestClient.Builder}
 * built by Spring Boot. Without this, a module that hangs (e.g. while starting
 * up) would keep a Tomcat thread blocked indefinitely; the timeouts make such
 * calls fail fast and surface as a diagnosable 502.
 *
 * Uses the JDK HttpClient (the Spring Boot default) rather than
 * {@code SimpleClientHttpRequestFactory}: the JDK client manages the
 * {@code Transfer-Encoding} header internally and does not expose it in the
 * response headers. HttpURLConnection would expose it, leaking a duplicate
 * {@code Transfer-Encoding: chunked} into the proxied response and breaking
 * strict reverse proxies (Tailscale Serve returns 502 on it).
 */
@Configuration
public class HttpClientConfig {

    @Bean
    public RestClientCustomizer restClientCustomizer() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(30));
        return builder -> builder.requestFactory(factory);
    }
}
