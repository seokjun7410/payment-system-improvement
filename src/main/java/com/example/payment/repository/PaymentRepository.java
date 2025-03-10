package com.example.payment.repository;

import com.example.payment.entity.Payment;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

	// "FINAL_COMPLETED"가 아닌 상태 중, 생성 시간이 threshold 이전인 Payment 목록 조회
	List<Payment> findByStatusNotAndCreatedAtBefore(String status, LocalDateTime threshold);

	// lectureId, userId, 특정 상태를 기준으로 Payment 조회
	Payment findByLectureIdAndUserIdAndStatus(Long lectureId, Long userId, String status);

	// 조건부 상태 변경: 특정 상태(fromStatus)인 경우에만 toStatus로 변경
	@Modifying
	@Query("UPDATE Payment p SET p.status = :toStatus WHERE p.lectureId = :lectureId AND p.userId = :userId AND p.status = :fromStatus")
	int updateStatusConditionally(@Param("lectureId") Long lectureId,
		@Param("userId") Long userId,
		@Param("fromStatus") String fromStatus,
		@Param("toStatus") String toStatus);

	@Modifying
	@Query("UPDATE Payment p SET p.status = :toStatus WHERE p.lectureId = :lectureId AND p.userId = :userId")
	int updateStatus(@Param("lectureId") Long lectureId,
		@Param("userId") Long userId,
		@Param("toStatus") String toStatus);
}