package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import java.util.List;

import org.springframework.data.domain.Page;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Page of results of a collection (Integration Standards, section 9)
 *
 * @param contenido elements of the page
 * @param page number of the page, starting at 0
 * @param size requested page size
 * @param totalElementos total number of elements that match the query
 * @param totalPaginas total number of pages
 * @param <T> type of the elements of the page
 */
public record PageResponse<T>(
        List<T> contenido,
        int page,
        int size,
        @JsonProperty("total_elementos") long totalElementos,
        @JsonProperty("total_paginas") int totalPaginas) {

        /**
     * Builds the response from a Spring Data page
     *
     * @param page Spring Data page
     * @param <T> type of the elements of the page
     * @return the response with the field names of the contract
     */
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
