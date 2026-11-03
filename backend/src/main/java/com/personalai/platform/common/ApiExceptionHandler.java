package com.personalai.platform.common;

import java.util.TreeMap;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler {
    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail notFound(ResourceNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(InvalidConfigurationException.class)
    ProblemDetail invalidConfiguration(InvalidConfigurationException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
        if (exception.field() != null) {
            problem.setProperty("errors", Map.of(exception.field(), exception.getMessage()));
        }
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException exception) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Check the highlighted fields and try again.");
        var errors = new TreeMap<String, String>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail malformedJson() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "The request body must contain valid JSON with the expected field types.");
    }
}
