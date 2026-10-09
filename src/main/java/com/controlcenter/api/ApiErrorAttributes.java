package com.controlcenter.api;

import java.util.List;
import java.util.Map;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.stereotype.Component;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Some API errors never reach a REST controller, so {@link ApiExceptionHandler} cannot see them:
 * an unknown {@code /api} route or an unsupported HTTP method is answered by Spring Boot's
 * {@code /error} endpoint. For {@code /api} paths this adds the {@code message} and {@code details}
 * fields, so every API error has the same shape as {@link ApiError}. Other paths keep the HTML
 * error page unchanged.
 */
@Component
public class ApiErrorAttributes extends DefaultErrorAttributes {

    @Override
    public Map<String, Object> getErrorAttributes(WebRequest webRequest, ErrorAttributeOptions options) {
        Map<String, Object> attributes = super.getErrorAttributes(webRequest, options);
        if (attributes.get("path") instanceof String path && path.startsWith("/api/")) {
            attributes.put("message", message(getError(webRequest), path, attributes));
            attributes.put("details", List.of());
        }
        return attributes;
    }

    /** Only messages built here are exposed; arbitrary exception text could leak internals. */
    private static String message(Throwable error, String path, Map<String, Object> attributes) {
        return switch (error) {
            case HttpRequestMethodNotSupportedException ex when ex.getSupportedMethods() != null ->
                    "Method %s is not supported by %s; use %s".formatted(ex.getMethod(), path,
                            String.join(", ", ex.getSupportedMethods()));
            case HttpRequestMethodNotSupportedException ex ->
                    "Method %s is not supported by %s".formatted(ex.getMethod(), path);
            case NoResourceFoundException ex -> "No API endpoint matches %s %s".formatted(ex.getHttpMethod(), path);
            case null, default -> String.valueOf(attributes.get("error"));
        };
    }
}
