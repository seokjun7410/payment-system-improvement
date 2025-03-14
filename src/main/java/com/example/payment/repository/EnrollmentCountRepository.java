package com.example.payment.repository;

import com.example.payment.entity.EnrollmentCount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface EnrollmentCountRepository extends JpaRepository<EnrollmentCount, Long> {

	@Modifying
	@Transactional
	@Query("UPDATE EnrollmentCount ec SET ec.currentCount = ec.currentCount + 1 " +
		"WHERE ec.lectureId = :lectureId AND ec.currentCount < ec.capacity")
	int tryIncrement(@Param("lectureId") Long lectureId);

	@Modifying
	@Transactional
	@Query("UPDATE EnrollmentCount ec SET ec.currentCount = ec.currentCount - 1 " +
		"WHERE ec.lectureId = :lectureId AND ec.currentCount > 0")
	int decrement(@Param("lectureId") Long lectureId);

	EnrollmentCount findByLectureId(long lectureId);



}