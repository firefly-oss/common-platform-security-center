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

package com.firefly.security.center.core.services;

import com.firefly.core.customer.sdk.api.PartiesApi;
import com.firefly.core.customer.sdk.api.NaturalPersonsApi;
import com.firefly.core.customer.sdk.api.LegalEntitiesApi;
import com.firefly.core.customer.sdk.api.EmailContactsApi;
import com.firefly.core.customer.sdk.api.PhoneContactsApi;
import com.firefly.core.customer.sdk.model.*;
import com.firefly.security.center.interfaces.dtos.CustomerInfoDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Service for resolving customer/party information from customer-mgmt using SDK.
 * 
 * <p>Uses the OpenAPI-generated PartiesApi client to fetch party information
 * and maps the SDK models to Security Center DTOs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerResolverService {

    private final PartiesApi partiesApi;
    private final NaturalPersonsApi naturalPersonsApi;
    private final LegalEntitiesApi legalEntitiesApi;
    private final EmailContactsApi emailContactsApi;
    private final PhoneContactsApi phoneContactsApi;

    /**
     * Fetches customer/party information from customer-mgmt service using SDK.
     * 
     * <p>Performs enrichment by fetching:
     * <ul>
     *   <li>Party base information</li>
     *   <li>Natural person or legal entity details for full name</li>
     *   <li>Primary email contact</li>
     *   <li>Primary phone contact</li>
     * </ul>
     *
     * @param partyId The party identifier
     * @return Mono<CustomerInfoDTO> with enriched customer information
     */
    public Mono<CustomerInfoDTO> resolveCustomerInfo(UUID partyId) {
        log.debug("Fetching enriched customer info for partyId: {}", partyId);

        return partiesApi.getPartyById(partyId, UUID.randomUUID().toString())
                .flatMap(party -> enrichCustomerInfo(party))
                .doOnSuccess(customer ->
                    log.debug("Successfully fetched enriched customer info for partyId: {}", partyId))
                .doOnError(error ->
                    log.error("Failed to fetch customer info for partyId: {}", partyId, error));
    }

    /**
     * Enriches party information with full name, email, and phone.
     * 
     * <p>Performs parallel enrichment calls to:
     * <ul>
     *   <li>Fetch natural person or legal entity for full name</li>
     *   <li>Fetch primary email contact</li>
     *   <li>Fetch primary phone contact</li>
     * </ul>
     *
     * @param party Base party information
     * @return Mono of enriched CustomerInfoDTO
     */
    private Mono<CustomerInfoDTO> enrichCustomerInfo(PartyDTO party) {
        UUID partyId = party.getPartyId();
        String partyKind = party.getPartyKind() != null ? party.getPartyKind().getValue() : "UNKNOWN";

        // Fetch full name (required)
        return fetchFullName(partyId, partyKind)
                .flatMap(fullName -> {
                    // Fetch optional email and phone in parallel
                    Mono<String> emailMono = fetchPrimaryEmail(partyId).defaultIfEmpty("");
                    Mono<String> phoneMono = fetchPrimaryPhone(partyId).defaultIfEmpty("");
                    
                    return Mono.zip(emailMono, phoneMono)
                            .map(tuple -> {
                                String email = tuple.getT1().isEmpty() ? null : tuple.getT1();
                                String phone = tuple.getT2().isEmpty() ? null : tuple.getT2();
                                
                                return CustomerInfoDTO.builder()
                                        .partyId(party.getPartyId())
                                        .partyKind(partyKind)
                                        .tenantId(party.getTenantId())
                                        .preferredLanguage(party.getPreferredLanguage())
                                        .fullName(fullName)
                                        .email(email)
                                        .phoneNumber(phone)
                                        .isActive(true)
                                        .build();
                            });
                });
    }

    /**
     * Fetches full name based on party kind (NATURAL_PERSON or LEGAL_ENTITY).
     */
    private Mono<String> fetchFullName(UUID partyId, String partyKind) {
        if ("INDIVIDUAL".equalsIgnoreCase(partyKind)) {
            return naturalPersonsApi.getNaturalPersonByPartyId(partyId, UUID.randomUUID().toString())
                    .map(this::buildFullNameFromNaturalPerson)
                    .doOnSuccess(name -> log.debug("Fetched natural person name for partyId: {}", partyId))
                    .doOnError(error -> log.error("Failed to fetch natural person for partyId: {}", partyId, error));
        } else if ("ORGANIZATION".equalsIgnoreCase(partyKind)) {
            return legalEntitiesApi.getLegalEntityByPartyId(partyId, UUID.randomUUID().toString())
                    .map(this::buildFullNameFromLegalEntity)
                    .doOnSuccess(name -> log.debug("Fetched legal entity name for partyId: {}", partyId))
                    .doOnError(error -> log.error("Failed to fetch legal entity for partyId: {}", partyId, error));
        } else {
            return Mono.error(new IllegalArgumentException(
                    "Unknown party kind: " + partyKind + " for partyId: " + partyId +
                    ". Expected 'INDIVIDUAL' or 'ORGANIZATION'."));
        }
    }

    /**
     * Builds full name from NaturalPersonDTO.
     */
    private String buildFullNameFromNaturalPerson(NaturalPersonDTO person) {
        StringBuilder name = new StringBuilder();
        if (person.getGivenName() != null) {
            name.append(person.getGivenName());
        }
        if (person.getMiddleName() != null && !person.getMiddleName().isEmpty()) {
            if (name.length() > 0) name.append(" ");
            name.append(person.getMiddleName());
        }
        if (person.getFamilyName1() != null) {
            if (name.length() > 0) name.append(" ");
            name.append(person.getFamilyName1());
        }
        if (person.getFamilyName2() != null && !person.getFamilyName2().isEmpty()) {
            if (name.length() > 0) name.append(" ");
            name.append(person.getFamilyName2());
        }
        return name.length() > 0 ? name.toString() : "Unknown Person";
    }

    /**
     * Builds full name from LegalEntityDTO.
     */
    private String buildFullNameFromLegalEntity(LegalEntityDTO entity) {
        if (entity.getTradeName() != null && !entity.getTradeName().isEmpty()) {
            return entity.getTradeName();
        }
        if (entity.getLegalName() != null && !entity.getLegalName().isEmpty()) {
            return entity.getLegalName();
        }
        return "Unknown Entity";
    }

    /**
     * Fetches primary email contact for party.
     * Returns empty Mono if no email found.
     */
    private Mono<String> fetchPrimaryEmail(UUID partyId) {
        FilterRequestEmailContactDTO filter = new FilterRequestEmailContactDTO();
        EmailContactDTO emailFilter = new EmailContactDTO();
        PaginationRequest pagination = new PaginationRequest();
        pagination.setPageNumber(0);
        pagination.setPageSize(100);
        filter.setPagination(pagination);
        filter.setFilters(emailFilter);
        filter.setPagination(pagination);
        // Note: Set filter criteria if needed to fetch only primary emails
        return emailContactsApi.filterEmailContacts(partyId, filter, null)
                .map(PaginationResponseEmailContactDTO::getContent)
                .flatMap(emails -> {
                    String email = emails.stream()
                            .filter(e -> e.getIsPrimary() != null && e.getIsPrimary())
                            .findFirst()
                            .map(e -> e.getEmail())
                            .orElse(null);
                    return Mono.justOrEmpty(email);
                })
                .onErrorResume(error -> {
                    log.debug("Failed to fetch email for partyId: {}", partyId);
                    return Mono.empty();
                });
    }

    /**
     * Fetches primary phone contact for party.
     * Returns empty Mono if no phone found.
     */
    private Mono<String> fetchPrimaryPhone(UUID partyId) {
        FilterRequestPhoneContactDTO filter = new FilterRequestPhoneContactDTO();
        // Note: Set filter criteria if needed to fetch only primary phones
        return phoneContactsApi.filterPhoneContacts(partyId, filter, null)
                .map(PaginationResponsePhoneContactDTO::getContent)
                .flatMap(phones -> {
                    String phone = phones.stream()
                            .filter(p -> p.getIsPrimary() != null && p.getIsPrimary())
                            .findFirst()
                            .map(p -> p.getPhoneNumber())
                            .orElse(null);
                    return Mono.justOrEmpty(phone);
                })
                .onErrorResume(error -> {
                    log.debug("Failed to fetch phone for partyId: {}", partyId);
                    return Mono.empty();
                });
    }
}
