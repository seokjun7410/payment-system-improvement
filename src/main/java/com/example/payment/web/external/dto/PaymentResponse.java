package com.example.payment.web.external.dto;

import lombok.Data;

@Data
public class PaymentResponse {
	private boolean success;
	private String message;
}