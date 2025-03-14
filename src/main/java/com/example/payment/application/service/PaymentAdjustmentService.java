package com.example.payment.application.service;

import com.example.payment.entity.Payment;
import com.example.payment.repository.PaymentRepository;
import com.example.payment.web.external.PgApiClient;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentAdjustmentService implements CommandLineRunner {

	private final PaymentRepository paymentRepository;
	private final EnrollmentService enrollmentService;
	private final PgApiClient pgApiClient;

	/**
	 * 스케줄러: 5분마다 미완료(PENDING 등) 결제를 대상으로 보정 프로세스를 수행합니다.
	 */
	@Scheduled(fixedDelay = 300000) // 300,000ms = 5분
	public void adjustPendingPayments() {
		LocalDateTime threshold = LocalDateTime.now().minusMinutes(5);
		List<Payment> pendingPayments = paymentRepository.findByStatusNotInAndCreatedAtBefore(List.of("EXCEEDS_CAPACITY","FINAL_COMPLETED"), threshold);

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
	 */
	public boolean checkPaymentStatusFromPG(Payment payment) {
		pgApiClient.mockFindPaymentHistoryApiCall(payment);
		return true;
	}
}