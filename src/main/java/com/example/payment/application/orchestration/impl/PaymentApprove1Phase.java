package com.example.payment.application.orchestration.impl;

import com.example.payment.application.event.FinalCompensationEvent;
import com.example.payment.application.event.FirstCompensationEvent;
import com.example.payment.application.event.SecondCompensationEvent;
import com.example.payment.application.exception.BusinessException;
import com.example.payment.application.orchestration.PaymentApproveOrchestration;
import com.example.payment.application.service.EnrollmentService;
import com.example.payment.web.controller.dto.PaymentRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@RequiredArgsConstructor
public class PaymentApprove1Phase implements PaymentApproveOrchestration {
	private final EnrollmentService enrollmentService;
	private final ApplicationEventPublisher eventPublisher;
	@Transactional
	public void approve(PaymentRequest paymentRequest) {
		Long userId = paymentRequest.getUserId();
		Long lectureId = paymentRequest.getLectureId();

		// 트랜잭션 1: 동시성 제어 - 수강 인원 증가 및 Payment 상태 변경(CREATED -> COUNT_UPDATED)
		try {
			enrollmentService.enrollmentCountTryIncrement(lectureId, userId);
		} catch (Exception e) {
			eventPublisher.publishEvent(
				new FirstCompensationEvent(this, lectureId, userId, "PG API 호출 실패: " + e.getMessage())
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
				new SecondCompensationEvent(this, lectureId, userId, "PG API 호출 실패: " + e.getMessage())
			);
			e.printStackTrace();
			throw new BusinessException("PG 결제 실패: " + e.getMessage());
		}

		// 트랜잭션 3: 구매강의 DB 반영 및 Payment 상태 변경(PAYMENT_PROCESSED -> FINAL_COMPLETED)
		try {
			enrollmentService.finalizeEnrollment(lectureId, userId);
		} catch (Exception e) {
			eventPublisher.publishEvent(
				new FinalCompensationEvent(this, lectureId, userId, "최종 결제 DB 반영 실패: " + e.getMessage())
			);
			e.printStackTrace();
			throw new BusinessException("최종 결제 반영 실패: " + e.getMessage());
		}

	}
}
