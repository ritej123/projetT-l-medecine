package com.telemed.platform.prescription.service;

public class ExternalLlmUnavailableException extends RuntimeException {

    public ExternalLlmUnavailableException(String message) {
        super(message);
    }
}
