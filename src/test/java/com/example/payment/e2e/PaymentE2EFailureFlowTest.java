package com.example.payment.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.payment.application.exception.BusinessException;
import com.example.payment.application.orchestration.PaymentApproveOrchestration;
import com.example.payment.application.service.EnrollmentService;
import com.example.payment.application.service.PaymentAdjustmentService;
import com.example.payment.entity.Enrollment;
import com.example.payment.entity.EnrollmentCount;
import com.example.payment.entity.Payment;
import com.example.payment.repository.EnrollmentCountRepository;
import com.example.payment.repository.EnrollmentRepository;
import com.example.payment.repository.PaymentRepository;
import com.example.payment.web.controller.dto.PaymentRequest;
import com.example.payment.web.external.PgApiClient;
import com.example.payment.web.external.dto.PaymentResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentE2EFailureFlowTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PaymentRepository paymentRepository;

	@Autowired
	private EnrollmentRepository enrollmentRepository;

	@Autowired
	private EnrollmentCountRepository enrollmentCountRepository;

	@Autowired
	private PaymentApproveOrchestration paymentApproveOrchestration;

	@Autowired
	private PaymentAdjustmentService paymentAdjustmentService;

	// 기본 테스트 데이터(강의 ID 1, 사용자 ID 1)를 위한 상수
	private final Long lectureId = 1L;
	private final Long userId = 1L;

	/**
	 * 각 테스트 실행 전에 DB를 초기화하고 기본 데이터를 삽입합니다.
	 * 각 시나리오에 따라 개별 테스트에서 추가 수정이 필요하면 그곳에서 별도 설정합니다.
	 */
	@BeforeEach
	void setUp() {
		// 모든 테이블 초기화
		enrollmentRepository.deleteAll();
		paymentRepository.deleteAll();
		enrollmentCountRepository.deleteAll();
	}

	/**
	 * [시나리오 1] 트랜잭션 1 실패(정원 초과) 테스트
	 * EnrollmentCount의 currentCount가 capacity와 같으면 동시성 제어 단계에서 실패하여
	 * BusinessException이 발생하고, 보상 이벤트는 발행되지 않아야 합니다.
	 */
	@Test
	void testStep1Failure_noCapacity() throws Exception {
		// EnrollmentCount를 정원이 꽉 찬 상태로 삽입
		EnrollmentCount ec = new EnrollmentCount();
		ec.setLectureId(lectureId);
		ec.setCurrentCount(100);
		ec.setCapacity(100);
		enrollmentCountRepository.save(ec);

		Payment payment = new Payment();
		payment.setLectureId(1L);
		payment.setUserId(1L);
		payment.setStatus("CREATED");
		payment.setCreatedAt(LocalDateTime.now().minusMinutes(1));
		paymentRepository.save(payment);

		// Payment는 컨트롤러 호출 시 새로 생성되므로 별도 삽입하지 않음

		PaymentRequest request = new PaymentRequest();
		// 기본 요청으로 실행하면 EnrollmentCount 조건에 의해 동시성 제어 실패 발생

		mockMvc.perform(post("/payment")
				.contentType(MediaType.APPLICATION_JSON)
				.content(asJsonString(request)))
			.andExpect(status().isBadRequest());

		// EnrollmentCount는 변동 없이 정원 그대로여야 함
		EnrollmentCount ecAfter = enrollmentCountRepository.findByLectureId(lectureId);
		assertThat(ecAfter.getCurrentCount()).isEqualTo(100);

		// Enrollment는 생성되지 않아야 함
		List<Enrollment> enrollments = enrollmentRepository.findAll();
		assertThat(enrollments).isEmpty();

		// Payment는 컨트롤러에서 생성되었으므로 하나 이상 존재하지만, 상태는 "CREATED"로 남아야 함
		List<Payment> payments = paymentRepository.findAll();
		assertThat(payments).isNotEmpty();
		Payment findPayment = payments.get(0);
		assertThat(findPayment.getStatus()).isEqualTo("CREATED");
	}

	/**
	 * [시나리오 2] 트랜잭션 2 실패 시뮬레이션
	 * 테스트 전용 PgApiExecutorService를 주입하여, PG API 호출에서 실패 응답을 반환하도록 합니다.
	 * 이 경우, LecturePaymentOrchestration은 PaymentCancellationEvent를 발행하고,
	 * CompensationService가 보상 로직을 수행하여 Payment 상태가 "CANCELLED" (또는 보상 실패 시 "CANCELLATION_FAILED")
	 * 로 업데이트되어야 합니다.
	 */
	@Test
	void testStep2Failure_compensationTriggered() throws Exception {
		CountDownLatch latch = new CountDownLatch(1);

		// EnrollmentCount 생성 (정원 여유)
		EnrollmentCount ec = new EnrollmentCount();
		ec.setLectureId(lectureId);
		ec.setCurrentCount(0);
		ec.setCapacity(100);
		enrollmentCountRepository.save(ec);

		Payment payment = new Payment();
		payment.setLectureId(lectureId);
		payment.setUserId(userId);
		payment.setStatus("CREATED");
		payment.setCreatedAt(LocalDateTime.now().minusMinutes(1));
		paymentRepository.save(payment);

		PaymentRequest request = new PaymentRequest();
		request.setUserId(userId);
		request.setLectureId(lectureId);

		mockMvc.perform(post("/payment")
				.contentType(MediaType.APPLICATION_JSON)
				.content(asJsonString(request)))
			.andExpect(status().isBadRequest());

		boolean completed = latch.await(2, TimeUnit.SECONDS);

		// CompensationService의 보상 로직이 비동기적으로 처리되어,
		// Payment 상태가 "CANCELLED" 또는 "CANCELLATION_FAILED" 중 하나로 업데이트되어야 함.
		Payment p = paymentRepository.findAll().get(0);
		assertThat(p.getStatus()).isIn("CANCELLED", "CANCELLATION_FAILED");

		// Enrollment는 생성되지 않아야 함.
		List<Enrollment> enrollments = enrollmentRepository.findAll();
		assertThat(enrollments).isEmpty();

		// EnrollmentCount: 트랜잭션 1는 정상적으로 증가했다가 보상으로 감소되어 0으로 복구되어야 함.
		EnrollmentCount ecAfter = enrollmentCountRepository.findByLectureId(lectureId);
		assertThat(ecAfter.getCurrentCount()).isEqualTo(0);
	}

	/**
	 * [시나리오 3] 트랜잭션 3 실패 시뮬레이션
	 * 테스트 전용 EnrollmentService 빈(TestEnrollmentService)을 주입하여,
	 * finalizeEnrollment()에서 강제로 예외를 발생시켜 FinalizationCompensationEvent 보상 로직을 유도합니다.
	 * 이 경우 Payment 상태가 "CANCELLED" 또는 "FINAL_COMPENSATION_FAILED"로 업데이트되어야 합니다.
	 */
	@Test
	void testStep3Failure_compensationTriggered() throws Exception {
		CountDownLatch latch = new CountDownLatch(1);

		// EnrollmentCount 생성 (정원 여유)
		EnrollmentCount ec = new EnrollmentCount();
		ec.setLectureId(lectureId);
		ec.setCurrentCount(0);
		ec.setCapacity(100);
		enrollmentCountRepository.save(ec);

		Payment payment = new Payment();
		payment.setLectureId(lectureId);
		payment.setUserId(userId);
		payment.setStatus("CREATED");
		payment.setCreatedAt(LocalDateTime.now().minusMinutes(1));
		paymentRepository.save(payment);

		PaymentRequest request = new PaymentRequest();
		request.setLectureId(lectureId);
		request.setUserId(userId);

		mockMvc.perform(post("/payment")
				.contentType(MediaType.APPLICATION_JSON)
				.content(asJsonString(request)))
			.andExpect(status().isBadRequest());

		boolean completed = latch.await(2, TimeUnit.SECONDS);

		// CompensationService의 보상 로직 실행 후, Payment 상태가 "CANCELLED" 또는 "FINAL_COMPENSATION_FAILED"로 업데이트되어야 함.
		Payment p = paymentRepository.findAll().get(0);
		assertThat(p.getStatus()).isIn("CANCELLED", "FINAL_COMPENSATION_FAILED");

		// Enrollment는 생성되지 않아야 함.
		List<Enrollment> enrollments = enrollmentRepository.findAll();
		assertThat(enrollments).isEmpty();

		// EnrollmentCount가 보상 처리로 인해 회복되어 0이어야 함.
		EnrollmentCount ecAfter = enrollmentCountRepository.findByLectureId(lectureId);
		assertThat(ecAfter.getCurrentCount()).isEqualTo(0);
	}

	// 유틸 메서드: 객체를 JSON 문자열로 변환
	private static String asJsonString(final Object obj) {
		try {
			return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * 테스트 전용 설정: PG API 호출 실패를 시뮬레이션하기 위해 TestPgApiExecutorService를 주입합니다.
	 */
	@TestConfiguration
	static class TestPgApiExecutorConfig {
		@Bean
		@Primary
		public PgApiClient testPgApiExecutorService() {
			return new PgApiClient() {
				@Override
				public PaymentResponse mockApproveApiCall(PaymentRequest request) {
					PaymentResponse response = new PaymentResponse();
					response.setSuccess(false);
					response.setMessage("Simulated PG API failure");
					return response;
				}
			};
		}
	}

	/**
	 * 테스트 전용 설정: finalizeEnrollment()에서 실패를 유도하기 위해 TestEnrollmentService를 주입합니다.
	 */
	@TestConfiguration
	static class TestEnrollmentServiceConfig {
		@Bean
		@Primary
		public EnrollmentService testEnrollmentService(EnrollmentCountRepository ecRepo,
			EnrollmentRepository eRepo,
			PaymentRepository pRepo,
			PgApiClient pgApiClient) {
			return new EnrollmentService(ecRepo, eRepo, pRepo, pgApiClient) {
				@Override
				public void finalizeEnrollment(Long lectureId, Long userId) {
					// Enrollment 생성 전 예외 발생을 통해 트랜잭션 3 실패 시뮬레이션
					throw new BusinessException("Simulated finalizeEnrollment failure");
				}
			};
		}
	}
}
