package com.example.backend.service;

public class AiServiceException extends RuntimeException {

    private final String errorCode;

    public AiServiceException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}