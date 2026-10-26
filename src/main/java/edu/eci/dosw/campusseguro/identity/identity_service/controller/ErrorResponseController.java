package edu.eci.dosw.campusseguro.identity.identity_service.controller;

import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.eci.dosw.campusseguro.identity.identity_service.dto.ErrorResponse;
import edu.eci.dosw.campusseguro.identity.identity_service.exception.ErrorCode;
import edu.eci.dosw.campusseguro.identity.identity_service.util.CorrelationIds;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Destino de los errores que ocurren fuera de los controladores, por ejemplo en un filtro, para que
 * también salgan con el ErrorResponse del estándar y no con la página de error por defecto (RNF-MS01-04)
 */
@RestController
public class ErrorResponseController implements ErrorController {

    /**
     * Construye el ErrorResponse a partir del estado que registró el contenedor, si la ruta se llama
     * directamente y no hay un error en curso responde 404
     *
     * @param request petición de error, de la que se toma el estado y el correlation id
     * @return la respuesta con el cuerpo de error normalizado
     */
    @RequestMapping("/error")
    public ResponseEntity<ErrorResponse> handleError(HttpServletRequest request) {
        int value = HttpStatus.NOT_FOUND.value();
        Object attribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (attribute instanceof Integer code) {
            value = code;
        }
        HttpStatus status = HttpStatus.resolve(value);
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        ErrorCode code = ErrorCode.forStatus(status.value());
        ErrorResponse body = ErrorResponse.of(status.value(), code.getCode(), code.getMessage(),
                CorrelationIds.current(request));
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}
