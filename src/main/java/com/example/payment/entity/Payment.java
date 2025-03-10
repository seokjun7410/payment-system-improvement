package com.example.payment.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "payment")
@Getter
@Setter
public class Payment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// 연동 대상 강의 ID
	private Long lectureId;

	// 결제 사용자 ID
	private Long userId;

	// 결제 상태 (예: PENDING, FINAL_COMPLETED, FAILED 등)
	private String status;

	// 결제 생성 시간
	private LocalDateTime createdAt;
}
