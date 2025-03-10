package com.example.payment.application.exception;

public class BusinessException extends RuntimeException {
	public BusinessException(String message) {
		super(message);
	}
}