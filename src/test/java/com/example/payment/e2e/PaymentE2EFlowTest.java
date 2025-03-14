package com.example.payment.e2e;

import com.example.payment.application.orchestration.PaymentApproveOrchestration;
import com.example.payment.application.service.PaymentAdjustmentService;
import com.example.payment.entity.Enrollment;
import com.example.payment.entity.EnrollmentCount;
import com.example.payment.entity.Payment;
import com.example.payment.repository.EnrollmentCountRepository;
import com.example.payment.repository.EnrollmentRepository;
import com.example.payment.repository.PaymentRepository;
import com.example.payment.web.controller.dto.PaymentRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentE2EFlowTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private EnrollmentRepository enrollmentRepository;

	@Autowired
	private EnrollmentCountRepository enrollmentCountRepository;

	@Autowired
	private PaymentRepository paymentRepository;

	@Autowired
	private PaymentApproveOrchestration paymentApproveOrchestration;

	@Autowired
	private PaymentAdjustmentService paymentAdjustmentService;

	// 테스트 시작 전 DB를 초기화합니다.
	@BeforeEach
	void setUp() {
		enrollmentRepository.deleteAll();
		paymentRepository.deleteAll();
		enrollmentCountRepository.deleteAll();
		enrollmentRepository.deleteAll();
		paymentRepository.deleteAll();
		enrollmentCountRepository.deleteAll();
	}

	/**
	 * 전체 플로우 테스트 1: /payment 엔드포인트 호출 후 정상 처리되는 경우
	 * - Payment, EnrollmentCount, Enrollment가 올바르게 생성되고, Payment 상태는 최종적으로 FINAL_COMPLETED 상태여야 합니다.
	 */
	@Test
	void testFullFlowSuccess() throws Exception {
		// [테스트 데이터 준비]
		// 1. EnrollmentCount: lectureId=1, 현재 수강 인원 0, 정원 10
		EnrollmentCount ec = new EnrollmentCount();
		ec.setLectureId(1L);
		ec.setCurrentCount(0);
		ec.setCapacity(10);
		enrollmentCountRepository.save(ec);

		// 2. Payment: lectureId=1, userId=1, 상태 "CREATED", 생성시간 현재로부터 1분 전
		Payment payment = new Payment();
		payment.setLectureId(1L);
		payment.setUserId(1L);
		payment.setStatus("CREATED");
		payment.setCreatedAt(LocalDateTime.now().minusMinutes(1));
		paymentRepository.save(payment);

		// 요청 시 사용할 PaymentRequest 객체 생성 (필요 필드 값 설정)
		PaymentRequest paymentRequest = new PaymentRequest();
		paymentRequest.setLectureId(1L);
		paymentRequest.setUserId(1L);

		// /payment 엔드포인트 호출
		mockMvc.perform(post("/payment")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(paymentRequest)))
			.andExpect(status().isOk());

		// EnrollmentCount가 생성되어 있어야 함 (강의 ID 1)
		EnrollmentCount findEc = enrollmentCountRepository.findByLectureId(1L);
		assertThat(findEc).isNotNull();
		// 동시성 제어가 성공하면 currentCount가 1 증가해야 함
		assertThat(findEc.getCurrentCount()).isEqualTo(1);

		// Payment 레코드가 생성되어야 하고, 최종 Payment 상태가 FINAL_COMPLETED여야 함
		List<Payment> payments = paymentRepository.findAll();
		assertThat(payments).hasSize(1);
		Payment findPayment = payments.get(0);
		assertThat(findPayment.getStatus()).isEqualTo("FINAL_COMPLETED");

		// Enrollment 레코드가 생성되어야 함
		List<Enrollment> enrollments = enrollmentRepository.findAll();
		assertThat(enrollments).hasSize(1);
		Enrollment enrollment = enrollments.get(0);
		assertThat(enrollment.getLectureId()).isEqualTo(1L);
		assertThat(enrollment.getUserId()).isEqualTo(1L);
	}

	/**
	 * 전체 플로우 테스트 2: 조정 프로세스 검증
	 * - Payment 상태가 FINAL_COMPLETED가 아닌 미완료 상태(PENDING)로 오래 남은 경우,
	 *   PaymentAdjustmentService.adjustPendingPayments()가 작동하여 상태를 FINAL_COMPLETED로 변경하고 Enrollment를 생성해야 합니다.
	 */
	@Test
	void testPaymentAdjustmentFlow() {
		// EnrollmentCount가 존재하지 않으면 새로 생성 (최대 정원 100)
		EnrollmentCount ec = new EnrollmentCount();
		ec.setLectureId(1L);
		ec.setCurrentCount(1);
		ec.setCapacity(100);
		enrollmentCountRepository.save(ec);

		// 미완료 상태의 Payment 레코드 생성 (PENDING 상태, 생성 시간이 5분 이전)
		Payment payment = new Payment();
		payment.setLectureId(1L);
		payment.setUserId(1L);
		payment.setStatus("PENDING");
		payment.setCreatedAt(LocalDateTime.now().minusMinutes(6));
		paymentRepository.save(payment);

		// 조정 프로세스 실행 (스케줄러가 아닌 직접 호출)
		paymentAdjustmentService.adjustPendingPayments();

		// Payment 상태가 FINAL_COMPLETED로 변경되었는지 확인
		Payment adjustedPayment = paymentRepository.findById(payment.getId()).orElse(null);
		assertThat(adjustedPayment).isNotNull();
		assertThat(adjustedPayment.getStatus()).isEqualTo("FINAL_COMPLETED");

		// Enrollment가 새로 생성되었는지 확인
		List<Enrollment> enrollments = enrollmentRepository.findAll();
		assertThat(enrollments).isNotEmpty();
		Enrollment enrollment = enrollments.get(0);
		assertThat(enrollment.getLectureId()).isEqualTo(1L);
		assertThat(enrollment.getUserId()).isEqualTo(1L);
	}
}
