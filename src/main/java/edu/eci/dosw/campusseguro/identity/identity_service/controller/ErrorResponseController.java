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
 * Destination of the errors that occur outside the controllers, for example in a filter, so that they also come out
 * with the ErrorResponse of the standard and not with the default error page (RNF-MS01-04)
 */
@RestController
public class ErrorResponseController implements ErrorController {

        /**
     * Builds the ErrorResponse from the status registered by the container, if the route is called directly and there
     * is no error in progress it answers 404
     *
     * @param request error request, from which the status and the correlation id are taken
     * @return the response with the normalized error body
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
