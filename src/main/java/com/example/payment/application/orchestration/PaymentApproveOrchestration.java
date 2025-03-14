package com.example.payment.application.orchestration;


import com.example.payment.web.controller.dto.PaymentRequest;

public interface PaymentApproveOrchestration {
	public void approve(PaymentRequest paymentRequest);
}

