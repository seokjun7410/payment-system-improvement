package com.example.payment.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

@Data
@Entity
@Table(name = "enrollment_count", uniqueConstraints = @UniqueConstraint(columnNames = "lectureId"))
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

}