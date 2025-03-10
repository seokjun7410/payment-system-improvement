package com.example.payment.application.event;


import com.example.payment.application.service.CompensationService;
import com.example.payment.application.event.FinalizationCompensationEvent;
import com.example.payment.application.event.PaymentCancellationEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentCompensationListener {

	private final CompensationService compensationService;

	@EventListener
	public void handlePaymentCancellation(PaymentCancellationEvent event) {
		compensationService.processPaymentCancellation(event);
	}

	@EventListener
	public void handleFinalizationCompensation(FinalizationCompensationEvent event) {
		compensationService.processFinalizationCompensation(event);
	}
}