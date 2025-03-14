package com.example.payment.web.external;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SlackApiClient {

	/**
	 * 슬랙 메시지 전송 (모킹): 재시도 모두 실패한 경우에만 호출됩니다.
	 */
	public void sendSlackAlert(String message) {
		// 실제 구현 시 슬랙 API 연동 코드 작성
		log.info("Sending Slack alert: {}", message);
	}

}
