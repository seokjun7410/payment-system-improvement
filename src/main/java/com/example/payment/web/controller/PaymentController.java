package com.example.payment.web.controller;

import com.example.payment.application.orchestration.PaymentApproveOrchestration;
import com.example.payment.web.controller.dto.PaymentRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController

public class PaymentController {
	private final PaymentApproveOrchestration paymentApproveOrchestration;

	public PaymentController(@Qualifier("paymentApprove3Phase") PaymentApproveOrchestration paymentApproveOrchestration) {
		this.paymentApproveOrchestration = paymentApproveOrchestration;
	}

	/**
	 * 인증과정에서 Payment가 CREATED 상태로 넘어온다고 가정, 인증과정 구현 X
	 */

	@PostMapping("/payment")
	public ResponseEntity<?> payment(@RequestBody PaymentRequest paymentRequest) {
		paymentApproveOrchestration.approve(paymentRequest);
		return ResponseEntity.ok().build();
	}

}
