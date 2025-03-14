package com.example.payment.application.event;

import lombok.Getter;
import lombok.Setter;
import org.springframework.context.ApplicationEvent;

@Getter
@Setter
public class FinalCompensationEvent extends ApplicationEvent {
	private final Long lectureId;
	private final Long userId;
	private final String reason;

	public FinalCompensationEvent(Object source, Long lectureId, Long userId, String reason) {
		super(source);
		this.lectureId = lectureId;
		this.userId = userId;
		this.reason = reason;
	}
}
