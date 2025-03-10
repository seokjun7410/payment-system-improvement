package com.example.payment.application.service;

import com.example.payment.entity.Payment;
import com.example.payment.repository.PaymentRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentAdjustmentService implements CommandLineRunner {

	private final PaymentRepository paymentRepository;
	private final EnrollmentService enrollmentService;

	/**
	 * 스케줄러: 5분마다 미완료(PENDING 등) 결제를 대상으로 보정 프로세스를 수행합니다.
	 */
	@Scheduled(fixedDelay = 300000) // 300,000ms = 5분
	public void adjustPendingPayments() {
		LocalDateTime threshold = LocalDateTime.now().minusMinutes(5);
		List<Payment> pendingPayments = paymentRepository.findByStatusNotAndCreatedAtBefore("FINAL_COMPLETED", threshold);

		for (Payment payment : pendingPayments) {
			try {
				// 실제 PG 결제내역 조회 API 호출 (모킹 처리)
				boolean isWithdrawn = checkPaymentStatusFromPG(payment);
				if (isWithdrawn) {
					// 실제 출금이 이루어진 경우, 최종 결제 DB 반영 재실행
					// Payment 상태 업데이트
					enrollmentService.adjustPayments(payment.getLectureId(), payment.getUserId());
					log.info("Payment adjusted to FINAL_COMPLETED for payment id: {}", payment.getId());
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("Error adjusting payment id: {}. {}", payment.getId(), e.getMessage());
			}
		}
	}

	/**
	 * 시스템 시작 시 미완료 결제가 있는 경우 즉시 보정 작업 수행
	 */
	@Override
	public void run(String... args) throws Exception {
		adjustPendingPayments();
	}

	/**
	 * 실제 PG 결제내역을 조회하는 모킹 메서드
	 * 실제 구현 시 외부 PG API를 호출하여 출금 여부를 확인하면 됩니다.
	 */
	public boolean checkPaymentStatusFromPG(Payment payment) {
		// 모킹: 실제 상황에 따라 true/false 처리 (여기서는 단순히 true 반환)
		return true;
	}
}