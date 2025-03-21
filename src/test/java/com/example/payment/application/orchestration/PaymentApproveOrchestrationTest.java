package com.example.payment.application.orchestration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.payment.application.event.FinalCompensationEvent;
import com.example.payment.application.event.SecondCompensationEvent;
import com.example.payment.application.event.FirstCompensationEvent;
import com.example.payment.application.exception.BusinessException;
import com.example.payment.application.orchestration.impl.PaymentApprove3Phase;
import com.example.payment.application.service.EnrollmentService;
import com.example.payment.web.controller.dto.PaymentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class PaymentApproveOrchestrationTest {

	@Mock
	private EnrollmentService enrollmentService;

	@Mock
	private ApplicationEventPublisher eventPublisher;

	@InjectMocks
	private PaymentApprove3Phase orchestration;

	private final Long lectureId = 1L;
	private final Long userId = 100L;
	private PaymentRequest paymentRequest;

	@BeforeEach
	void setUp() {
		paymentRequest = new PaymentRequest();
		paymentRequest.setLectureId(lectureId);  // 1L 설정
		paymentRequest.setUserId(userId);        // 100L 설정
		// 필요한 필드 초기화 (필요시 추가)
	}

	/**
	 * 모든 단계가 정상 실행될 경우 예외 없이 진행되어야 하며,
	 * 보상 이벤트는 발행되지 않아야 합니다.
	 */
	@Test
	void testEnrollLecture_success() {
		// 각 단계 성공(아무런 예외가 발생하지 않음)라고 가정
		assertDoesNotThrow(() -> orchestration.approve(paymentRequest));

		// 각 단계가 한 번씩 호출되었는지 검증
		verify(enrollmentService, times(1)).enrollmentCountTryIncrement(lectureId, userId);
		verify(enrollmentService, times(1)).processPayment(paymentRequest);
		verify(enrollmentService, times(1)).finalizeEnrollment(lectureId, userId);
		// 보상 이벤트는 발행되지 않아야 함
		verify(eventPublisher, never()).publishEvent(any());
	}

	/**
	 * 트랜잭션 1(동시성 제어) 실패 시
	 * BusinessException이 발생하고 FirstCompensationEvent 발행
	 */
	@Test
	void testEnrollLecture_failureInStep1() {
		doThrow(new BusinessException("수강 인원이 모두 찼습니다."))
			.when(enrollmentService).enrollmentCountTryIncrement(lectureId, userId);

		BusinessException ex = assertThrows(BusinessException.class,
			() -> orchestration.approve(paymentRequest));

		assertTrue(ex.getMessage().contains("동시성 제어 실패"));
		// 트랜잭션1 실패이므로 다른 단계는 호출되지 않아야 함
		verify(enrollmentService, times(1)).enrollmentCountTryIncrement(lectureId, userId);
		verify(enrollmentService, never()).processPayment(any());
		verify(enrollmentService, never()).finalizeEnrollment(anyLong(), anyLong());

		// 보상 이벤트 발행 검증
		ArgumentCaptor<FirstCompensationEvent> eventCaptor = ArgumentCaptor.forClass(
			FirstCompensationEvent.class);
		verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());
		FirstCompensationEvent publishedEvent = eventCaptor.getValue();
		assertEquals(lectureId, publishedEvent.getLectureId());
		assertEquals(userId, publishedEvent.getUserId());
		assertTrue(publishedEvent.getReason().contains("동시성 제어 실패"));
	}

	/**
	 * 트랜잭션 2(PG API 호출) 실패 시,
	 * PaymentCancellationEvent가 발행되고 BusinessException이 발생해야 합니다.
	 */
	@Test
	void testEnrollLecture_failureInStep2() {
		// 트랜잭션 1은 성공한다고 가정
		doNothing().when(enrollmentService).enrollmentCountTryIncrement(lectureId, userId);
		// 트랜잭션 2에서 예외 발생
		doThrow(new BusinessException("PG 결제 실패"))
			.when(enrollmentService).processPayment(paymentRequest);

		BusinessException ex = assertThrows(BusinessException.class,
			() -> orchestration.approve(paymentRequest));

		assertTrue(ex.getMessage().contains("PG 결제 실패"));
		verify(enrollmentService, times(1)).enrollmentCountTryIncrement(lectureId, userId);
		verify(enrollmentService, times(1)).processPayment(paymentRequest);
		verify(enrollmentService, never()).finalizeEnrollment(anyLong(), anyLong());

		// PaymentCancellationEvent가 발행되었는지 검증
		ArgumentCaptor<SecondCompensationEvent> eventCaptor = ArgumentCaptor.forClass(
			SecondCompensationEvent.class);
		verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());
		SecondCompensationEvent publishedEvent = eventCaptor.getValue();
		assertEquals(lectureId, publishedEvent.getLectureId());
		assertEquals(userId, publishedEvent.getUserId());
		assertTrue(publishedEvent.getReason().contains("PG 결제 실패"));
	}

	/**
	 * 트랜잭션 3(최종 DB 반영) 실패 시,
	 * FinalizationCompensationEvent가 발행되고 BusinessException이 발생해야 합니다.
	 */
	@Test
	void testEnrollLecture_failureInStep3() {
		// 트랜잭션 1, 2는 성공한다고 가정
		doNothing().when(enrollmentService).enrollmentCountTryIncrement(lectureId, userId);
		doNothing().when(enrollmentService).processPayment(paymentRequest);
		// 트랜잭션 3에서 예외 발생
		doThrow(new BusinessException("최종 결제 반영 실패"))
			.when(enrollmentService).finalizeEnrollment(lectureId, userId);

		BusinessException ex = assertThrows(BusinessException.class,
			() -> orchestration.approve(paymentRequest));

		assertTrue(ex.getMessage().contains("최종 결제 반영 실패"));
		verify(enrollmentService, times(1)).enrollmentCountTryIncrement(lectureId, userId);
		verify(enrollmentService, times(1)).processPayment(paymentRequest);
		verify(enrollmentService, times(1)).finalizeEnrollment(lectureId, userId);

		// FinalizationCompensationEvent가 발행되었는지 검증
		ArgumentCaptor<FinalCompensationEvent> eventCaptor = ArgumentCaptor.forClass(
			FinalCompensationEvent.class);
		verify(eventPublisher, times(1)).publishEvent(eventCaptor.capture());
		FinalCompensationEvent publishedEvent = eventCaptor.getValue();
		assertEquals(lectureId, publishedEvent.getLectureId());
		assertEquals(userId, publishedEvent.getUserId());
		assertTrue(publishedEvent.getReason().contains("최종 결제 DB 반영 실패"));
	}
}
