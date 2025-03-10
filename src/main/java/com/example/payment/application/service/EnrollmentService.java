package com.example.payment.application.service;

import com.example.payment.entity.Payment;
import com.example.payment.web.controller.dto.PaymentRequest;
import com.example.payment.web.external.dto.PaymentResponse;
import com.example.payment.application.exception.BusinessException;
import com.example.payment.repository.EnrollmentRepository;
import com.example.payment.repository.PaymentRepository;
import com.example.payment.entity.Enrollment;
import com.example.payment.repository.EnrollmentCountRepository;
import com.example.payment.web.external.PgApiExecutorService;
import lombok.RequiredArgsConstructor;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EnrollmentService {

	private final EnrollmentCountRepository enrollmentCountRepository;
	private final EnrollmentRepository enrollmentRepository;
	private final PaymentRepository paymentRepository;
	private final PgApiExecutorService pgApiExecutorService;

	/**
	 * 트랜잭션 1: 동시성 제어 - 수강 인원 증가
	 * 성공 시 Payment 상태를 CREATED -> COUNT_UPDATED 로 조건부 업데이트합니다.
	 */
	@Transactional
	public void enrollmentCountTryIncrement(Long lectureId, Long userId) {
		int updatedRows = enrollmentCountRepository.tryIncrement(lectureId);
		if (updatedRows == 0) {
			throw new BusinessException("수강 인원이 모두 찼습니다.");
		}
		// Payment 상태 조건부 업데이트: CREATED 상태인 경우에만 COUNT_UPDATED로 변경
		int paymentUpdated = paymentRepository.updateStatusConditionally(lectureId, userId, "CREATED", "COUNT_UPDATED");
		if (paymentUpdated == 0) {
			throw new BusinessException("Payment 상태 업데이트 실패(CREATED -> COUNT_UPDATED).");
		}
	}

	/**
	 * 트랜잭션 2: PG API 호출
	 * Spring Retry를 이용하여 최대 3회, 지터 백오프를 적용합니다.
	 * 성공 시 Payment 상태를 COUNT_UPDATED -> PAYMENT_PROCESSED 로 조건부 업데이트합니다.
	 */
	@Retryable(
		value = Exception.class,
		maxAttempts = 3,
		backoff = @Backoff(delay = 2000, maxDelay = 5000, random = true)
	)
	@Transactional
	public void processPayment(PaymentRequest paymentRequest, Long lectureId, Long userId) {
		PaymentResponse response = pgApiExecutorService.mockPaymentApiCall(paymentRequest);
		if (!response.isSuccess()) {
			throw new BusinessException("결제 실패");
		}
		// Payment 상태 조건부 업데이트: COUNT_UPDATED 상태인 경우에만 PAYMENT_PROCESSED로 변경
		int paymentUpdated = paymentRepository.updateStatusConditionally(lectureId, userId, "COUNT_UPDATED", "PAYMENT_PROCESSED");
		if (paymentUpdated == 0) {
			throw new BusinessException("Payment 상태 업데이트 실패(COUNT_UPDATED -> PAYMENT_PROCESSED).");
		}
	}

	/**
	 * 트랜잭션 3: 구매강의 DB 반영 및 결제 완료
	 * Enrollment 기록 생성과 함께 Payment 상태를 PAYMENT_PROCESSED -> FINAL_COMPLETED 로 업데이트합니다.
	 */
	@Transactional
	public void finalizeEnrollment(Long lectureId, Long userId) {
		// Enrollment 기록 생성
		Enrollment enrollment = new Enrollment();
		enrollment.setLectureId(lectureId);
		enrollment.setUserId(userId);
		enrollmentRepository.save(enrollment);

		// Payment 상태 조건부 업데이트: PAYMENT_PROCESSED 상태인 경우에만 FINAL_COMPLETED로 변경
		int paymentUpdated = paymentRepository.updateStatusConditionally(lectureId, userId, "PAYMENT_PROCESSED", "FINAL_COMPLETED");
		if (paymentUpdated == 0) {
			throw new BusinessException("Payment 상태 업데이트 실패(PAYMENT_PROCESSED -> FINAL_COMPLETED).");
		}
	}

	@Transactional
	public void adjustPayments(Long lectureId, Long userId) {
		Enrollment enrollment = new Enrollment();
		enrollment.setLectureId(lectureId);
		enrollment.setUserId(userId);
		enrollmentRepository.save(enrollment);

		// Payment 상태 조건부 업데이트: PAYMENT_PROCESSED 상태인 경우에만 FINAL_COMPLETED로 변경
		int paymentUpdated = paymentRepository.updateStatus(lectureId, userId, "FINAL_COMPLETED");
		if (paymentUpdated == 0) {
			throw new BusinessException("Payment 상태 업데이트 실패(PAYMENT_PROCESSED -> FINAL_COMPLETED).");
		}
	}
}
