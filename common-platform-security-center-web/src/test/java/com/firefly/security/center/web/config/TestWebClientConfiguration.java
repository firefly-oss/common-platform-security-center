/*
 * Copyright 2025 Firefly Software Solutions Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.firefly.security.center.web.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Test configuration for WebClient beans
 */
@TestConfiguration
public class TestWebClientConfiguration {

    @Bean
    @Primary
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    @Bean
    @Primary
    public WebClient customerMgmtWebClient(WebClient.Builder builder) {
        return builder.baseUrl("http://localhost:8081").build();
    }

    @Bean
    @Primary
    public WebClient contractMgmtWebClient(WebClient.Builder builder) {
        return builder.baseUrl("http://localhost:8082").build();
    }

    @Bean
    @Primary
    public WebClient productMgmtWebClient(WebClient.Builder builder) {
        return builder.baseUrl("http://localhost:8083").build();
    }

    @Bean
    @Primary
    public WebClient referenceMasterDataWebClient(WebClient.Builder builder) {
        return builder.baseUrl("http://localhost:8084").build();
    }
}
