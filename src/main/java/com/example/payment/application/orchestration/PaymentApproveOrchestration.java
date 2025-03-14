package com.example.payment.application.orchestration;


import com.example.payment.web.controller.dto.PaymentRequest;

public interface PaymentApproveOrchestration {
	void approve(PaymentRequest paymentRequest);
}

