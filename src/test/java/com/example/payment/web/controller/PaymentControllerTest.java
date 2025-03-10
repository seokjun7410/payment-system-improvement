package com.example.payment.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.payment.entity.Enrollment;
import com.example.payment.entity.EnrollmentCount;
import com.example.payment.entity.Payment;
import com.example.payment.repository.EnrollmentCountRepository;
import com.example.payment.repository.EnrollmentRepository;
import com.example.payment.repository.PaymentRepository;
import com.example.payment.web.controller.dto.PaymentRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private PaymentRepository paymentRepository;

	@Autowired
	private EnrollmentRepository enrollmentRepository;

	@Autowired
	private EnrollmentCountRepository enrollmentCountRepository;

	@BeforeEach
	void setUp() {
		// 테스트 환경 정리를 위해 각 Repository의 데이터를 삭제합니다.
		enrollmentRepository.deleteAll();
		paymentRepository.deleteAll();
		enrollmentCountRepository.deleteAll();

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
	}

	@Test
	void testPaymentEndpoint_actualBehavior() throws Exception {


		PaymentRequest paymentRequest = new PaymentRequest();
		paymentRequest.setAmount(100.0);
		paymentRequest.setUserId(1L);

		// 실제 API 호출 (실제 비즈니스 로직이 실행됨)
		mockMvc.perform(post("/payment")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(paymentRequest)))
			.andExpect(status().isOk());

		// API 호출 후, Enrollment가 생성되었는지 확인합니다.
		Optional<Enrollment> enrollmentOpt = enrollmentRepository.findAll().stream()
			.filter(e -> e.getLectureId().equals(1L) && e.getUserId().equals(1L))
			.findFirst();
		assertThat(enrollmentOpt).isPresent();

		// Payment 상태가 "FINAL_COMPLETED"로 업데이트되었는지 확인합니다.
		Optional<Payment> paymentOpt = paymentRepository.findAll().stream()
			.filter(p -> p.getLectureId().equals(1L) && p.getUserId().equals(1L))
			.findFirst();
		assertThat(paymentOpt).isPresent();
		assertThat(paymentOpt.get().getStatus()).isEqualTo("FINAL_COMPLETED");
	}
}
