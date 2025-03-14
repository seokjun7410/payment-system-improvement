package com.example.payment.application.event;


import com.example.payment.application.service.CompensationService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentCompensationListener {

	private final CompensationService compensationService;
	@EventListener
	@Async
	public void firstPhaseCompensation(PaymentStatusToCancelEvent event) {
		compensationService.updateCancelStatus(event);
	}

	@EventListener
	@Async
	public void secondPhaseCompensation(PaymentCancellationEvent event) {
		compensationService.processPaymentCancellation(event);
	}

	@EventListener
	@Async
	public void finalPhaseCompensation(FinalizationCompensationEvent event) {
		compensationService.processFinalizationCompensation(event);
	}

}