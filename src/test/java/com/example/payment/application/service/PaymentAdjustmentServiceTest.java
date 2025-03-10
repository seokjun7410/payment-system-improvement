package com.example.payment.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.payment.entity.Payment;
import com.example.payment.repository.PaymentRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

class TestPaymentAdjustmentService extends PaymentAdjustmentService {
	private final boolean withdrawStatus;

	public TestPaymentAdjustmentService(PaymentRepository paymentRepository, EnrollmentService enrollmentService, boolean withdrawStatus) {
		super(paymentRepository, enrollmentService);
		this.withdrawStatus = withdrawStatus;
	}

	@Override
	public boolean checkPaymentStatusFromPG(Payment payment) {
		return withdrawStatus;
	}
}

@ExtendWith(MockitoExtension.class)
class PaymentAdjustmentServiceTest {

	@Mock
	private PaymentRepository paymentRepository;

	@Mock
	private EnrollmentService enrollmentService;

	private Payment pendingPayment;

	/**
	 * 묵킹으로 true가 고정되어 있어 테스트마다 서로 다른 withdrawStatus 값을 설정할 수 있도록 임시처리
	 */
	private TestPaymentAdjustmentService testService;

	private final Long lectureId = 1L;
	private final Long userId = 100L;

	@BeforeEach
	void setUp() {
		pendingPayment = new Payment();
		pendingPayment.setId(10L);
		pendingPayment.setLectureId(lectureId);
		pendingPayment.setUserId(userId);
		// 생성시간을 현재 시간에서 6분 전으로 설정하여 threshold 조건을 만족하도록 함.
		pendingPayment.setCreatedAt(LocalDateTime.now().minusMinutes(6));
		// 초기 상태는 PENDING 상태라고 가정
		pendingPayment.setStatus("PENDING");
	}

	/**
	 * 성공 케이스: PG 결제내역 조회가 true를 반환하면
	 * EnrollmentService.finalizeEnrollment 호출, Payment 상태가 FINAL_COMPLETED로 업데이트되어 저장됨.
	 */
	@Test
	void testAdjustPendingPayments_success() {
		// TestPaymentAdjustmentService의 withdrawStatus를 true로 설정
		testService = new TestPaymentAdjustmentService(paymentRepository, enrollmentService, true);

		// PaymentRepository에서 pendingPayment 반환
		when(paymentRepository.findByStatusNotAndCreatedAtBefore(eq("FINAL_COMPLETED"), any(LocalDateTime.class)))
			.thenReturn(List.of(pendingPayment));

		// 조정 프로세스 실행
		testService.adjustPendingPayments();

		// EnrollmentService.finalizeEnrollment가 호출되었는지 검증
		verify(enrollmentService, times(1)).adjustPayments(pendingPayment.getLectureId(), pendingPayment.getUserId());

	}

	/**
	 * 실패 케이스: PG 결제내역 조회가 false를 반환하면
	 * EnrollmentService.finalizeEnrollment 호출되지 않고 Payment 상태 변경없이 저장됨.
	 */
	@Test
	void testAdjustPendingPayments_noWithdrawal() {
		// TestPaymentAdjustmentService의 withdrawStatus를 false로 설정
		testService = new TestPaymentAdjustmentService(paymentRepository, enrollmentService, false);

		when(paymentRepository.findByStatusNotAndCreatedAtBefore(eq("FINAL_COMPLETED"), any(LocalDateTime.class)))
			.thenReturn(List.of(pendingPayment));

		testService.adjustPendingPayments();

		// EnrollmentService.finalizeEnrollment가 호출되지 않아야 함.
		verify(enrollmentService, never()).finalizeEnrollment(anyLong(), anyLong());

		// Payment 상태가 변경되지 않으므로, save() 호출은 없거나 같은 상태로 저장
		// 만약 save()가 호출되면 상태가 변경되지 않았는지 확인
		ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
		verify(paymentRepository, atMost(1)).save(paymentCaptor.capture());
		if (!paymentCaptor.getAllValues().isEmpty()) {
			Payment savedPayment = paymentCaptor.getValue();
			assertNotEquals("FINAL_COMPLETED", savedPayment.getStatus());
		}
	}

	/**
	 * run() 메서드 테스트: run() 호출 시 adjustPendingPayments()가 실행됨.
	 */
	@Test
	void testRun_callsAdjustPendingPayments() throws Exception {
		// TestPaymentAdjustmentService의 withdrawStatus 값은 상관없이
		testService = new TestPaymentAdjustmentService(paymentRepository, enrollmentService, true);
		// 조정 프로세스가 실행될 때 아무 Payment가 없도록 설정
		when(paymentRepository.findByStatusNotAndCreatedAtBefore(eq("FINAL_COMPLETED"), any(LocalDateTime.class)))
			.thenReturn(List.of());

		// run() 호출
		testService.run();

		// PaymentRepository.findByStatusNotAndCreatedAtBefore가 호출되었는지 검증
		verify(paymentRepository, times(1))
			.findByStatusNotAndCreatedAtBefore(eq("FINAL_COMPLETED"), any(LocalDateTime.class));
	}
}
