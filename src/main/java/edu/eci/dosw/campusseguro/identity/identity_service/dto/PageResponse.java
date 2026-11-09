package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import java.util.List;

import org.springframework.data.domain.Page;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Página de resultados de una colección (Estándares de Integración, sección 9)
 *
 * @param contenido elementos de la página
 * @param page número de la página, desde 0
 * @param size tamaño de página solicitado
 * @param totalElementos cantidad total de elementos que cumplen la consulta
 * @param totalPaginas cantidad total de páginas
 * @param <T> tipo de los elementos de la página
 */
public record PageResponse<T>(
        List<T> contenido,
        int page,
        int size,
        @JsonProperty("total_elementos") long totalElementos,
        @JsonProperty("total_paginas") int totalPaginas) {

    /**
     * Construye la respuesta a partir de una página de Spring Data
     *
     * @param page página de Spring Data
     * @param <T> tipo de los elementos de la página
     * @return la respuesta con los nombres de campo del contrato
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
