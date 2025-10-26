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

package com.firefly.security.center.core.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configuration for WebClient beans to communicate with downstream services
 */
@Configuration
public class WebClientConfiguration {

    @Value("${firefly.security-center.clients.customer-mgmt.base-url}")
    private String customerMgmtBaseUrl;

    @Value("${firefly.security-center.clients.contract-mgmt.base-url}")
    private String contractMgmtBaseUrl;

    @Value("${firefly.security-center.clients.product-mgmt.base-url}")
    private String productMgmtBaseUrl;

    @Value("${firefly.security-center.clients.reference-master-data.base-url}")
    private String referenceMasterDataBaseUrl;

    @Bean
    public WebClient customerMgmtWebClient(WebClient.Builder webClientBuilder) {
        return webClientBuilder
                .baseUrl(customerMgmtBaseUrl)
                .build();
    }

    @Bean
    public WebClient contractMgmtWebClient(WebClient.Builder webClientBuilder) {
        return webClientBuilder
                .baseUrl(contractMgmtBaseUrl)
                .build();
    }

    @Bean
    public WebClient productMgmtWebClient(WebClient.Builder webClientBuilder) {
        return webClientBuilder
                .baseUrl(productMgmtBaseUrl)
                .build();
    }

    @Bean
    public WebClient referenceMasterDataWebClient(WebClient.Builder webClientBuilder) {
        return webClientBuilder
                .baseUrl(referenceMasterDataBaseUrl)
                .build();
    }
}
