package com.bonifybank.bonify.exception;

public class EntityNotFoundException extends BusinessException {

    public EntityNotFoundException(String entity, Integer id) {
        super(ErrorCodes.NOT_FOUND, entity + " con id " + id + " non trovato");
    }
}
