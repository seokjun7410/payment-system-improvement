package com.example.payment.web.controller;

import com.example.payment.application.orchestration.LecturePaymentOrchestration;
import com.example.payment.entity.EnrollmentCount;
import com.example.payment.entity.Payment;
import com.example.payment.repository.EnrollmentCountRepository;
import com.example.payment.repository.PaymentRepository;
import com.example.payment.web.controller.dto.PaymentRequest;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PaymentController {
	private final LecturePaymentOrchestration lecturePaymentOrchestration;

	/**
	 * 인증과정에서 Payment가 CREATE 상태로 넘어온다고 가정, 인증과정 구현 X
	 */

	@PostMapping("/payment")
	public ResponseEntity<?> payment(@RequestBody PaymentRequest paymentRequest) {
		// 예제에서는 강의 ID와 사용자 ID를 고정합니다.
		long userId = 1L;
		long lectureId = 1L;

		lecturePaymentOrchestration.enrollLecture(lectureId, userId, paymentRequest);
		return ResponseEntity.ok().build();
	}
}
