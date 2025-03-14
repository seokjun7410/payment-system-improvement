package com.example.payment.application.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.payment.application.event.FinalizationCompensationEvent;
import com.example.payment.application.event.PaymentCancellationEvent;
import com.example.payment.repository.EnrollmentCountRepository;
import com.example.payment.repository.PaymentRepository;
import com.example.payment.web.external.PgApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompensationServiceTest {

	@Mock
	private EnrollmentCountRepository enrollmentCountRepository;

	@Mock
	private PaymentRepository paymentRepository;

	@Mock
	private PgApiClient pgApiClient;

	@InjectMocks
	private CompensationService compensationService;

	private final Long lectureId = 1L;
	private final Long userId = 100L;
	private final String reason = "Test failure reason";

	private PaymentCancellationEvent cancellationEvent;
	private FinalizationCompensationEvent finalizationEvent;

	@BeforeEach
	void setUp() {
		cancellationEvent = new PaymentCancellationEvent(this, lectureId, userId, reason);
		finalizationEvent = new FinalizationCompensationEvent(this, lectureId, userId, reason);
	}

	// ================================
	// processPaymentCancellation 테스트
	// ================================

	// 성공 케이스: 보상 로직이 정상적으로 수행되어 Payment 상태가 COUNT_UPDATED -> CANCELLED로 변경됨.
	@Test
	void testProcessPaymentCancellation_success() {
		// enrollmentCountRepository.decrement()는 void 메서드이므로 doNothing()이 기본 동작.
		// updateStatusConditionally가 정상 동작하면 1을 반환한다고 가정.
		when(paymentRepository.updateStatusConditionally(lectureId, userId, "COUNT_UPDATED", "CANCELLED"))
			.thenReturn(1);

		assertDoesNotThrow(() -> compensationService.processPaymentCancellation(cancellationEvent));

		verify(enrollmentCountRepository, times(1)).decrement(lectureId);
		verify(paymentRepository, times(1))
			.updateStatusConditionally(lectureId, userId, "COUNT_UPDATED", "CANCELLED");
	}

	// 실패 케이스: Payment 상태 업데이트 실패 (반환값 0)이면 RuntimeException 발생
	@Test
	void testProcessPaymentCancellation_failure() {
		when(paymentRepository.updateStatusConditionally(lectureId, userId, "COUNT_UPDATED", "CANCELLED"))
			.thenReturn(0);

		RuntimeException exception = assertThrows(RuntimeException.class, () ->
			compensationService.processPaymentCancellation(cancellationEvent)
		);
		assertTrue(exception.getMessage().contains("Failed to update Payment status during PaymentCancellation compensation"));

		verify(enrollmentCountRepository, times(1)).decrement(lectureId);
		verify(paymentRepository, times(1))
			.updateStatusConditionally(lectureId, userId, "COUNT_UPDATED", "CANCELLED");
	}

	// ================================
	// processFinalizationCompensation 테스트
	// ================================

	// 성공 케이스: 보상 로직이 정상적으로 수행되어 Payment 상태가 PAYMENT_PROCESSED -> CANCELLED로 변경됨.
	@Test
	void testProcessFinalizationCompensation_success() {
		when(paymentRepository.updateStatusConditionally(lectureId, userId, "PAYMENT_PROCESSED", "CANCELLED"))
			.thenReturn(1);

		assertDoesNotThrow(() -> compensationService.processFinalizationCompensation(finalizationEvent));

		verify(enrollmentCountRepository, times(1)).decrement(lectureId);
		verify(paymentRepository, times(1))
			.updateStatusConditionally(lectureId, userId, "PAYMENT_PROCESSED", "CANCELLED");
	}

	// 실패 케이스: Payment 상태 업데이트 실패 (반환값 0)이면 RuntimeException 발생
	@Test
	void testProcessFinalizationCompensation_failure() {
		when(paymentRepository.updateStatusConditionally(lectureId, userId, "PAYMENT_PROCESSED", "CANCELLED"))
			.thenReturn(0);

		RuntimeException exception = assertThrows(RuntimeException.class, () ->
			compensationService.processFinalizationCompensation(finalizationEvent)
		);
		assertTrue(exception.getMessage().contains("Failed to update Payment status during Finalization compensation"));

		verify(enrollmentCountRepository, times(1)).decrement(lectureId);
		verify(paymentRepository, times(1))
			.updateStatusConditionally(lectureId, userId, "PAYMENT_PROCESSED", "CANCELLED");
	}

	// =============================================
	// @Recover 메서드 테스트 (fallback 로직)
	// =============================================

	// PaymentCancellationEvent의 @Recover 테스트
	@Test
	void testRecoverPaymentCancellation() {
		// recover 메서드 호출 전, updateStatusConditionally가 호출되었을 때 1을 반환하도록 모킹
		when(paymentRepository.updateStatusConditionally(lectureId, userId, "COUNT_UPDATED", "CANCELLATION_FAILED"))
			.thenReturn(1);

		// @Recover 메서드는 재시도 실패 후 fallback 호출 시 실행되므로, 직접 호출하여 검증
		compensationService.recoverPaymentCancellation(new Exception("Test Exception"), cancellationEvent);

		verify(paymentRepository, times(1))
			.updateStatusConditionally(lectureId, userId, "COUNT_UPDATED", "CANCELLATION_FAILED");
		// sendSlackAlert은 내부에서 로그로 처리하므로 별도 검증(로그 확인)을 생략하거나 스파이로 검증할 수 있음
	}

	// FinalizationCompensationEvent의 @Recover 테스트
	@Test
	void testRecoverFinalizationCompensation() {
		when(paymentRepository.updateStatusConditionally(lectureId, userId, "PAYMENT_PROCESSED", "FINAL_COMPENSATION_FAILED"))
			.thenReturn(1);

		compensationService.recoverFinalizationCompensation(new Exception("Test Exception"), finalizationEvent);

		verify(paymentRepository, times(1))
			.updateStatusConditionally(lectureId, userId, "PAYMENT_PROCESSED", "FINAL_COMPENSATION_FAILED");
	}
}
