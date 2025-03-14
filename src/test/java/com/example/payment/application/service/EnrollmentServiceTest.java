package com.example.payment.application.service;

import com.example.payment.application.exception.BusinessException;
import com.example.payment.entity.Enrollment;
import com.example.payment.repository.EnrollmentCountRepository;
import com.example.payment.repository.EnrollmentRepository;
import com.example.payment.repository.PaymentRepository;
import com.example.payment.web.controller.dto.PaymentRequest;
import com.example.payment.web.external.PgApiClient;
import com.example.payment.web.external.dto.PaymentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnrollmentServiceTest {

	@Mock
	private EnrollmentCountRepository enrollmentCountRepository;

	@Mock
	private EnrollmentRepository enrollmentRepository;

	@Mock
	private PaymentRepository paymentRepository;

	@Mock
	private PgApiClient pgApiClient;

	@InjectMocks
	private EnrollmentService enrollmentService;

	private final Long lectureId = 1L;
	private final Long userId = 100L;

	@BeforeEach
	void setUp() {
		// 초기화 필요한 작업 (없으면 생략)
	}

	// =========================
	// [트랜잭션 1] 동시성 제어 테스트
	// =========================

	// 성공 케이스: tryIncrement와 Payment 상태 업데이트 모두 성공
	@Test
	void testEnrollmentCountTryIncrement_success() {
		when(enrollmentCountRepository.tryIncrement(lectureId)).thenReturn(1);
		when(paymentRepository.updateStatusConditionally(lectureId, userId, "CREATED", "COUNT_UPDATED"))
			.thenReturn(1);

		assertDoesNotThrow(() -> enrollmentService.enrollmentCountTryIncrement(lectureId, userId));

		verify(enrollmentCountRepository, times(1)).tryIncrement(lectureId);
		verify(paymentRepository, times(1))
			.updateStatusConditionally(lectureId, userId, "CREATED", "COUNT_UPDATED");
	}

	// 실패 케이스 1: tryIncrement 실패 (정원 초과)
	@Test
	void testEnrollmentCountTryIncrement_failure_dueToNoCapacity() {
		when(enrollmentCountRepository.tryIncrement(lectureId)).thenReturn(0);

		BusinessException exception = assertThrows(BusinessException.class,
			() -> enrollmentService.enrollmentCountTryIncrement(lectureId, userId));

		assertTrue(exception.getMessage().contains("수강 인원이 모두 찼습니다"));
		verify(enrollmentCountRepository, times(1)).tryIncrement(lectureId);
		verify(paymentRepository, never()).updateStatusConditionally(anyLong(), anyLong(), anyString(), anyString());
	}

	// 실패 케이스 2: Payment 상태 업데이트 실패 (CREATED -> COUNT_UPDATED)
	@Test
	void testEnrollmentCountTryIncrement_failure_dueToStatusUpdate() {
		when(enrollmentCountRepository.tryIncrement(lectureId)).thenReturn(1);
		when(paymentRepository.updateStatusConditionally(lectureId, userId, "CREATED", "COUNT_UPDATED"))
			.thenReturn(0);

		BusinessException exception = assertThrows(BusinessException.class,
			() -> enrollmentService.enrollmentCountTryIncrement(lectureId, userId));

		assertTrue(exception.getMessage().contains("Payment 상태 업데이트 실패(CREATED -> COUNT_UPDATED)"));
		verify(enrollmentCountRepository, times(1)).tryIncrement(lectureId);
		verify(paymentRepository, times(1))
			.updateStatusConditionally(lectureId, userId, "CREATED", "COUNT_UPDATED");
	}

	// =========================
	// [트랜잭션 2] PG API 호출 테스트
	// =========================

	// 성공 케이스: PG API 호출 성공 및 Payment 상태 업데이트 성공
	@Test
	void testProcessPayment_success() {
		PaymentRequest request = new PaymentRequest();
		request.setLectureId(lectureId);  // 1L 설정
		request.setUserId(userId);        // 100L 설정

		PaymentResponse successResponse = new PaymentResponse();
		successResponse.setSuccess(true);
		successResponse.setMessage("Payment Success");

		when(pgApiClient.mockApproveApiCall(ArgumentMatchers.eq(request)))
			.thenReturn(successResponse);
		when(paymentRepository.updateStatusConditionally(lectureId, userId, "COUNT_UPDATED", "PAYMENT_PROCESSED"))
			.thenReturn(1);

		assertDoesNotThrow(() -> enrollmentService.processPayment(request));

		verify(pgApiClient, times(1)).mockApproveApiCall(request);
		verify(paymentRepository, times(1))
			.updateStatusConditionally(lectureId, userId, "COUNT_UPDATED", "PAYMENT_PROCESSED");
	}

	// 실패 케이스 1: PG API 호출 실패 (응답 성공 플래그 false)
	@Test
	void testProcessPayment_failure_dueToApiFailure() {
		PaymentRequest request = new PaymentRequest();
		PaymentResponse failedResponse = new PaymentResponse();
		failedResponse.setSuccess(false);
		failedResponse.setMessage("Payment Failed");

		when(pgApiClient.mockApproveApiCall(ArgumentMatchers.eq(request)))
			.thenReturn(failedResponse);

		BusinessException exception = assertThrows(BusinessException.class,
			() -> enrollmentService.processPayment(request));

		assertTrue(exception.getMessage().contains("결제 실패"));
		verify(pgApiClient, times(1)).mockApproveApiCall(request);
		verify(paymentRepository, never())
			.updateStatusConditionally(anyLong(), anyLong(), eq("COUNT_UPDATED"), eq("PAYMENT_PROCESSED"));
	}

	// 실패 케이스 2: Payment 상태 업데이트 실패 (COUNT_UPDATED -> PAYMENT_PROCESSED)
	@Test
	void testProcessPayment_failure_dueToStatusUpdate() {
		PaymentRequest request = new PaymentRequest();
		request.setLectureId(lectureId);  // 1L 설정
		request.setUserId(userId);        // 100L 설정

		PaymentResponse successResponse = new PaymentResponse();
		successResponse.setSuccess(true);
		successResponse.setMessage("Payment Success");

		when(pgApiClient.mockApproveApiCall(ArgumentMatchers.eq(request)))
			.thenReturn(successResponse);
		when(paymentRepository.updateStatusConditionally(lectureId, userId, "COUNT_UPDATED", "PAYMENT_PROCESSED"))
			.thenReturn(0);

		BusinessException exception = assertThrows(BusinessException.class,
			() -> enrollmentService.processPayment(request));

		assertTrue(exception.getMessage().contains("Payment 상태 업데이트 실패(COUNT_UPDATED -> PAYMENT_PROCESSED)"));
		verify(pgApiClient, times(1)).mockApproveApiCall(request);
		verify(paymentRepository, times(1))
			.updateStatusConditionally(lectureId, userId, "COUNT_UPDATED", "PAYMENT_PROCESSED");
	}

	// =========================
	// [트랜잭션 3] 최종 DB 반영 테스트
	// =========================

	// 성공 케이스: Enrollment 저장 및 Payment 상태 업데이트 성공
	@Test
	void testFinalizeEnrollment_success() {
		Enrollment enrollment = new Enrollment();
		enrollment.setLectureId(lectureId);
		enrollment.setUserId(userId);

		when(enrollmentRepository.save(any(Enrollment.class))).thenReturn(enrollment);
		when(paymentRepository.updateStatusConditionally(lectureId, userId, "PAYMENT_PROCESSED", "FINAL_COMPLETED"))
			.thenReturn(1);

		assertDoesNotThrow(() -> enrollmentService.finalizeEnrollment(lectureId, userId));

		verify(enrollmentRepository, times(1)).save(any(Enrollment.class));
		verify(paymentRepository, times(1))
			.updateStatusConditionally(lectureId, userId, "PAYMENT_PROCESSED", "FINAL_COMPLETED");
	}

	// 실패 케이스: Payment 상태 업데이트 실패 (PAYMENT_PROCESSED -> FINAL_COMPLETED)
	@Test
	void testFinalizeEnrollment_failure_dueToStatusUpdate() {
		Enrollment enrollment = new Enrollment();
		enrollment.setLectureId(lectureId);
		enrollment.setUserId(userId);

		when(enrollmentRepository.save(any(Enrollment.class))).thenReturn(enrollment);
		when(paymentRepository.updateStatusConditionally(lectureId, userId, "PAYMENT_PROCESSED", "FINAL_COMPLETED"))
			.thenReturn(0);

		BusinessException exception = assertThrows(BusinessException.class,
			() -> enrollmentService.finalizeEnrollment(lectureId, userId));

		assertTrue(exception.getMessage().contains("Payment 상태 업데이트 실패(PAYMENT_PROCESSED -> FINAL_COMPLETED)"));
		verify(enrollmentRepository, times(1)).save(any(Enrollment.class));
		verify(paymentRepository, times(1))
			.updateStatusConditionally(lectureId, userId, "PAYMENT_PROCESSED", "FINAL_COMPLETED");
	}
}
