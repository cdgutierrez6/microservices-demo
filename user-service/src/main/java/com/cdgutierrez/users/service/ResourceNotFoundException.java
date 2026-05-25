package com.cdgutierrez.users.service;

import java.util.UUID;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String entity, UUID id) {
        super(entity + " not found with id: " + id);
    }
}
