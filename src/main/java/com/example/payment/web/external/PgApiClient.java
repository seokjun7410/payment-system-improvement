package com.example.payment.web.external;

import com.example.payment.application.event.FinalCompensationEvent;
import com.example.payment.entity.Payment;
import com.example.payment.web.controller.dto.PaymentRequest;
import com.example.payment.web.external.dto.PaymentResponse;
import org.springframework.stereotype.Component;

@Component
public class PgApiClient {
	/** 사용하는 PG에 따라 맞는 멱등성 보장 방법을 구현해야 합니다. 단순화를 위해 생략합니다. **/
	public PaymentResponse mockApproveApiCall(PaymentRequest request)  {
		PaymentResponse response = new PaymentResponse();
		response.setSuccess(true);
		response.setMessage("Payment Success");
		return response;
	}

	/** 사용하는 PG에 따라 맞는 멱등성 보장 방법을 구현해야 합니다. 단순화를 위해 생략합니다. **/
	public boolean mockCancelApiCall(FinalCompensationEvent event)  {
		return true;
	}

	/** 사용하는 PG에 따라 맞는 멱등성 보장 방법을 구현해야 합니다. 단순화를 위해 생략합니다. **/
	public boolean mockFindPaymentHistoryApiCall(Payment payment)  {
		return true;
	}
}
