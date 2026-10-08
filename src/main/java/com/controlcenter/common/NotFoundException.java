package com.controlcenter.common;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String resource, Object id) {
        super("%s with id %s was not found".formatted(resource, id));
    }
}
