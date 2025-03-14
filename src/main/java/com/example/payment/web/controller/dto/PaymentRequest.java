package com.example.payment.web.controller.dto;

import lombok.Data;

@Data
public class PaymentRequest {
	private Long userId;
	private Long lectureId;
	private Double amount;
	// 기타 결제 관련 정보 (예: 카드번호, 결제타입 등)
}