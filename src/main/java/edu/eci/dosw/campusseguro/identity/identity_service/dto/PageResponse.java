package edu.eci.dosw.campusseguro.identity.identity_service.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public class PageResponse<T> {
    private final List<T> contenido;
    private final int pagina;
    private final int tamano;
    private final long totalElementos;
    private final int totalPaginas;

    public PageResponse(List<T> contenido, int pagina, int tamano, long totalElementos, int totalPaginas) {
        this.contenido = contenido;
        this.pagina = pagina;
        this.tamano = tamano;
        this.totalElementos = totalElementos;
        this.totalPaginas = totalPaginas;
    }

    public static <T> PageResponse<T> from(Page<T> p) {
        return new PageResponse<>(
            p.getContent(), 
            p.getNumber(), 
            p.getSize(), 
            p.getTotalElements(), 
            p.getTotalPages()
        );
    }

    public List<T> getContenido() { return contenido; }

    public int getPagina() { return pagina; }

    public int getTamano() { return tamano; }

    public long getTotalElementos() { return totalElementos; }

    public int getTotalPaginas() { return totalPaginas; }
}
