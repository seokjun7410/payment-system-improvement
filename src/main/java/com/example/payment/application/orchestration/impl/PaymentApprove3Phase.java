package com.example.payment.application.orchestration.impl;

import com.example.payment.application.event.FinalizationCompensationEvent;
import com.example.payment.application.event.PaymentCancellationEvent;
import com.example.payment.application.event.PaymentStatusToCancelEvent;
import com.example.payment.application.exception.BusinessException;
import com.example.payment.application.orchestration.PaymentApproveOrchestration;
import com.example.payment.application.service.EnrollmentService;
import com.example.payment.web.controller.dto.PaymentRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Primary
public class PaymentApprove3Phase implements PaymentApproveOrchestration {

	private final EnrollmentService enrollmentService;
	private final ApplicationEventPublisher eventPublisher;
	/**
	 * 전체 결제 프로세스 오케스트레이션
	 * 각 단계에서 실패 시 해당 보상 이벤트를 발행합니다.
	 */
	public void approve(PaymentRequest paymentRequest) {
		// 트랜잭션 1: 동시성 제어 - 수강 인원 증가 및 Payment 상태 변경(CREATED -> COUNT_UPDATED)
		Long lectureId = paymentRequest.getLectureId();
		Long userId = paymentRequest.getUserId();

		try {
			enrollmentService.enrollmentCountTryIncrement(lectureId, userId);
		} catch (Exception e) {
			eventPublisher.publishEvent(
				new PaymentStatusToCancelEvent(this, lectureId, userId, "동시성 제어 실패: " + e.getMessage())
			);
			if(!(e instanceof BusinessException))
				e.printStackTrace();
			throw new BusinessException("동시성 제어 실패: " + e.getMessage());
		}

		// 트랜잭션 2: PG API 호출 및 Payment 상태 변경(COUNT_UPDATED -> PAYMENT_PROCESSED)
		try {
			enrollmentService.processPayment(paymentRequest);
		} catch (Exception e) {
			eventPublisher.publishEvent(
				new PaymentCancellationEvent(this, lectureId, userId, "PG 결제 실패: " + e.getMessage())
			);
			e.printStackTrace();
			throw new BusinessException("PG 결제 실패: " + e.getMessage());
		}

		// 트랜잭션 3: 구매강의 DB 반영 및 Payment 상태 변경(PAYMENT_PROCESSED -> FINAL_COMPLETED)
		try {
			enrollmentService.finalizeEnrollment(lectureId, userId);
		} catch (Exception e) {
			eventPublisher.publishEvent(
				new FinalizationCompensationEvent(this, lectureId, userId, "최종 결제 DB 반영 실패: " + e.getMessage())
			);
			e.printStackTrace();
			throw new BusinessException("최종 결제 반영 실패: " + e.getMessage());
		}
	}

}
