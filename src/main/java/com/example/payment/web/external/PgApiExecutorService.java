package com.example.payment.web.external;

import com.example.payment.web.external.dto.PaymentResponse;
import com.example.payment.web.controller.dto.PaymentRequest;
import org.springframework.stereotype.Service;

@Service
public class PgApiExecutorService {
	public PaymentResponse mockPaymentApiCall(PaymentRequest request) {
		PaymentResponse response = new PaymentResponse();
		// 외부 PG API 호출 대신 항상 성공으로 모킹 (필요 시 실패 시나리오 구현 가능)
		response.setSuccess(true);
		response.setMessage("Payment Success");
		return response;
	}
}
