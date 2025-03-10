package com.example.payment.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;

@Data
@Entity
@Table(name = "enrollment_count")
public class EnrollmentCount {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// 강의 ID
	private Long lectureId;

	// 현재 수강 인원
	private int currentCount;

	// 최대 정원
	private int capacity;

	// 낙관적 락을 위한 버전 관리
	@Version
	private Long version;
}