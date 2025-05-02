package com.ratedistribution.rdp.advice;

import com.ratedistribution.rdp.utilities.exceptions.RateNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

/**
 * GlobalExceptionHandler handles application-wide exceptions and provides custom error responses.
 * This class extends {@link ResponseEntityExceptionHandler} to handle various web-related exceptions.
 *
 * @author Ömer Asaf BALIKÇI
 */

@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    /**
     * Handles RateNotFoundException and returns a NOT_FOUND (404) response.
     *
     * @param exception the RateNotFoundException thrown when a requested rate is not found
     * @param request   the HttpServletRequest object
     * @return ResponseEntity containing error details and HTTP status
     */
    @ExceptionHandler(RateNotFoundException.class)
    public ResponseEntity<Object> handleRateNotFoundException(RateNotFoundException exception, HttpServletRequest request) {
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.NOT_FOUND, exception.getMessage());
        errorResponse.setPath(request.getRequestURI());
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    /**
     * Handles validation errors in method arguments and returns a BAD_REQUEST response.
     *
     * @param exception  the MethodArgumentNotValidException
     * @param headers    the HttpHeaders
     * @param statusCode the HttpStatusCode
     * @param request    the WebRequest
     * @return ResponseEntity containing the validation error details
     */
    @Override
    public ResponseEntity<Object> handleMethodArgumentNotValid(@NonNull MethodArgumentNotValidException exception,
                                                               @NonNull HttpHeaders headers,
                                                               @NonNull HttpStatusCode statusCode,
                                                               @NonNull WebRequest request) {
        ServletWebRequest servletWebRequest = (ServletWebRequest) request;
        List<FieldError> fieldErrors = exception.getBindingResult().getFieldErrors();
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.BAD_REQUEST, exception.getMessage());
        errorResponse.addValidationError(fieldErrors);
        errorResponse.setPath(servletWebRequest.getRequest().getRequestURI());
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles errors related to unreadable HTTP message requests (e.g., malformed JSON).
     * Returns a BAD_REQUEST response.
     *
     * @param exception  the HttpMessageNotReadableException
     * @param headers    the HttpHeaders
     * @param statusCode the HttpStatusCode
     * @param request    the WebRequest
     * @return ResponseEntity containing the error details
     */
    @Override
    public ResponseEntity<Object> handleHttpMessageNotReadable(@NonNull HttpMessageNotReadableException exception,
                                                               @NonNull HttpHeaders headers,
                                                               @NonNull HttpStatusCode statusCode,
                                                               @NonNull WebRequest request) {
        ServletWebRequest servletWebRequest = (ServletWebRequest) request;
        ErrorResponse errorResponse = new ErrorResponse(HttpStatus.BAD_REQUEST, "Malformed Json Request", exception);
        errorResponse.setPath(servletWebRequest.getRequest().getRequestURI());
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }
}
