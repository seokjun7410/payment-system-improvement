package com.example.payment.application.service;

import com.example.payment.repository.PaymentRepository;
import com.example.payment.application.event.FinalizationCompensationEvent;
import com.example.payment.application.event.PaymentCancellationEvent;
import com.example.payment.repository.EnrollmentCountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CompensationService {

	private final EnrollmentCountRepository enrollmentCountRepository;
	private final PaymentRepository paymentRepository;

	/**
	 * 트랜잭션 2 보상: PG API 호출 실패에 따른 보상 처리
	 * - 수강 인원 감소 및 외부 결제 취소 API(모킹) 호출 후,
	 *   Payment 상태를 COUNT_UPDATED → CANCELLED 로 조건부 업데이트합니다.
	 */
	@Transactional
	@Retryable(value = Exception.class, maxAttempts = 3,
		backoff = @Backoff(delay = 1000, maxDelay = 3000, random = true))
	public void processPaymentCancellation(PaymentCancellationEvent event) {
		// 보상 로직: 수강 인원 감소
		enrollmentCountRepository.decrement(event.getLectureId());

		// 외부 결제 취소 API 호출 (모킹 처리)
		// pgApiClient.cancelPayment(...);

		// 보상 성공 시 Payment 상태 업데이트: COUNT_UPDATED → CANCELLED
		int updated = paymentRepository.updateStatusConditionally(
			event.getLectureId(), event.getUserId(), "COUNT_UPDATED", "CANCELLED"
		);
		if (updated == 0) {
			throw new RuntimeException("Failed to update Payment status during PaymentCancellation compensation");
		}
		log.info("PaymentCancellation compensation successful for lectureId: {}, userId: {}",
			event.getLectureId(), event.getUserId());
	}

	/**
	 * 트랜잭션 3 보상: 구매 DB 반영 실패에 따른 보상 처리
	 * - 수강 인원 감소 및 외부 출금 취소 API(모킹) 호출 후,
	 *   Payment 상태를 PAYMENT_PROCESSED → CANCELLED 로 조건부 업데이트합니다.
	 */
	@Transactional
	@Retryable(value = Exception.class, maxAttempts = 3,
		backoff = @Backoff(delay = 1000, maxDelay = 3000, random = true))
	public void processFinalizationCompensation(FinalizationCompensationEvent event) {
		// 보상 로직: 수강 인원 감소
		enrollmentCountRepository.decrement(event.getLectureId());

		// 외부 출금 취소 API 호출 (모킹 처리)
		// pgApiClient.cancelWithdrawal(...);

		// 보상 성공 시 Payment 상태 업데이트: PAYMENT_PROCESSED → CANCELLED
		int updated = paymentRepository.updateStatusConditionally(
			event.getLectureId(), event.getUserId(), "PAYMENT_PROCESSED", "CANCELLED"
		);
		if (updated == 0) {
			throw new RuntimeException("Failed to update Payment status during Finalization compensation");
		}
		log.info("Finalization compensation successful for lectureId: {}, userId: {}",
			event.getLectureId(), event.getUserId());
	}

	/**
	 * PaymentCancellationEvent 보상 로직 최종 실패 시 fallback 처리.
	 * 모든 재시도가 실패한 경우 Payment 상태를 CANCELLATION_FAILED로 업데이트하고, 슬랙 알림 전송.
	 */
	@Recover
	public void recoverPaymentCancellation(Exception e, PaymentCancellationEvent event) {
		int updated = paymentRepository.updateStatusConditionally(
			event.getLectureId(), event.getUserId(), "COUNT_UPDATED", "CANCELLATION_FAILED"
		);
		log.error("Payment cancellation compensation FAILED for lectureId: {}, userId: {}. Reason: {}. Updated rows: {}",
			event.getLectureId(), event.getUserId(), event.getReason(), updated, e);
		sendSlackAlert("Payment cancellation compensation FAILED for lectureId: " +
			event.getLectureId() + ", userId: " + event.getUserId() + ". Reason: " + e.getMessage());
	}

	/**
	 * FinalizationCompensationEvent 보상 로직 최종 실패 시 fallback 처리.
	 * 모든 재시도가 실패한 경우 Payment 상태를 FINAL_COMPENSATION_FAILED로 업데이트하고, 슬랙 알림 전송.
	 */
	@Recover
	public void recoverFinalizationCompensation(Exception e, FinalizationCompensationEvent event) {
		int updated = paymentRepository.updateStatusConditionally(
			event.getLectureId(), event.getUserId(), "PAYMENT_PROCESSED", "FINAL_COMPENSATION_FAILED"
		);
		log.error("Finalization compensation FAILED for lectureId: {}, userId: {}. Reason: {}. Updated rows: {}",
			event.getLectureId(), event.getUserId(), event.getReason(), updated, e);
		sendSlackAlert("Finalization compensation FAILED for lectureId: " +
			event.getLectureId() + ", userId: " + event.getUserId() + ". Reason: " + e.getMessage());
	}

	/**
	 * 슬랙 메시지 전송 (모킹): 재시도 모두 실패한 경우에만 호출됩니다.
	 */
	private void sendSlackAlert(String message) {
		// 실제 구현 시 슬랙 API 연동 코드 작성
		log.info("Sending Slack alert: {}", message);
	}
}
