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
	public void firstPhaseCompensation(FirstCompensationEvent event) {
		compensationService.firstCompensationProcess(event);
	}

	@EventListener
	@Async
	public void secondPhaseCompensation(SecondCompensationEvent event) {
		compensationService.secondCompensationProcess(event);
	}

	@EventListener
	@Async
	public void finalPhaseCompensation(FinalCompensationEvent event) {
		compensationService.finalCompensationProcess(event);
	}

}