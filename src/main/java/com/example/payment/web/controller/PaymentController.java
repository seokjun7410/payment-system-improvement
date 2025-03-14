package com.example.payment.web.controller;

import com.example.payment.application.orchestration.PaymentApproveOrchestration;
import com.example.payment.entity.EnrollmentCount;
import com.example.payment.entity.Payment;
import com.example.payment.repository.EnrollmentCountRepository;
import com.example.payment.repository.PaymentRepository;
import com.example.payment.web.controller.dto.PaymentRequest;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController

public class PaymentController {
	private final PaymentApproveOrchestration paymentApproveOrchestration;
	private final EnrollmentCountRepository enrollmentCountRepository;
	private final PaymentRepository paymentRepository;
	public PaymentController(@Qualifier("paymentApprove3Phase") PaymentApproveOrchestration paymentApproveOrchestration,
		EnrollmentCountRepository enrollmentCountRepository, PaymentRepository paymentRepository) {
		this.paymentApproveOrchestration = paymentApproveOrchestration;
		this.enrollmentCountRepository = enrollmentCountRepository;
		this.paymentRepository = paymentRepository;
	}
	private static long num = 1;

	/**
	 * 인증과정에서 Payment가 CREATE 상태로 넘어온다고 가정, 인증과정 구현 X
	 */

	@PostMapping("/payment")
	public ResponseEntity<?> payment(@RequestBody PaymentRequest paymentRequest) {
//		paymentRequest.setUserId(num);
//		dummy();
		paymentApproveOrchestration.approve(paymentRequest);
		return ResponseEntity.ok().build();
	}
	
	
	@Transactional
	public void dummy() {
		Payment payment = new Payment();
		payment.setLectureId(1L);
		payment.setUserId(num++);
		payment.setStatus("CREATED");
		payment.setCreatedAt(LocalDateTime.now().minusMinutes(1));
		paymentRepository.save(payment);


	}

	@PostMapping("/dummy")
	public ResponseEntity<?> lec() {
		// 2. Payment: lectureId=1, userId=1, 상태 "CREATED", 생성시간 현재로부터 1분 전
		EnrollmentCount ec = new EnrollmentCount();
		ec.setLectureId(1L);
		ec.setCurrentCount(0);
		ec.setCapacity(10);
		enrollmentCountRepository.save(ec);
		return ResponseEntity.ok().build();
	}
}
