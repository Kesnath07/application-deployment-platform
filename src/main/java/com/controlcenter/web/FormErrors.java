package com.controlcenter.web;

import java.util.stream.Collectors;
import org.springframework.validation.BindingResult;

final class FormErrors {

    private FormErrors() {
    }

    /** Flattens field errors into a single flash message for redirect-after-post forms. */
    static String summarize(BindingResult result) {
        return result.getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
    }
}
