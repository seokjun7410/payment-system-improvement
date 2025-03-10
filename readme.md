다음은 변경된 패키지 및 클래스 이름을 반영한 최종 버전의 GitHub README 한글 번역본입니다.

---

# Payment System

이 프로젝트는 Spring Boot 기반의 결제 시스템 예제로, 동시 구매 처리를 위해 조건부 업데이트를 활용하며, 다중 트랜잭션 오케스트레이션과 보상 이벤트를 통해 데이터 일관성을 유지하는 방식을 구현합니다. 시스템은 Spring Retry(지터 백오프 지원)와 스케줄링을 사용하여 미완료 결제에 대한 주기적인 조정을 수행합니다.

## 개요

이 프로젝트는 아래와 같은 결제 처리 워크플로우를 구현합니다.

- **조건부 업데이트:**  
  별도의 `EnrollmentCount` 테이블을 통해 강의 수강 인원을 관리하며, `Payment` 엔티티의 상태를 조건부 업데이트 방식으로 변경합니다.

- **다중 트랜잭션 처리:**  
  결제 처리는 세 단계의 트랜잭션으로 분리됩니다.
    1. **트랜잭션 1 (동시성 제어):**  
       수강 인원 증가와 함께 `Payment` 상태를 `CREATED`에서 `COUNT_UPDATED`로 조건부 업데이트합니다.
    2. **트랜잭션 2 (PG API 호출):**  
       외부 PG API(모킹)를 호출하여 결제를 처리하고, `Payment` 상태를 `COUNT_UPDATED`에서 `PAYMENT_PROCESSED`로 변경합니다.
    3. **트랜잭션 3 (최종 DB 반영):**  
       `Enrollment` 레코드를 생성하고, `Payment` 상태를 `PAYMENT_PROCESSED`에서 `FINAL_COMPLETED`로 업데이트합니다.

- **보상(Compensation) 이벤트:**  
  각 트랜잭션 단계에서 오류 발생 시 보상 이벤트가 발행됩니다.
    - **트랜잭션 2 보상:**  
      PG API 호출 실패 시, `PaymentCancellationEvent` 이벤트가 발행되어 증가된 수강 인원을 복구하고 `Payment` 상태를 `CANCELLED`로 업데이트합니다. 만약 보상 로직 재시도 후에도 실패하면 상태는 `CANCELLATION_FAILED`로 변경되고 Slack 알림(모킹)을 전송합니다.
    - **트랜잭션 3 보상:**  
      최종 DB 반영 실패 시, `FinalizationCompensationEvent` 이벤트가 발행되어 보상 로직이 실행됩니다. 보상 성공 시 `Payment` 상태가 `CANCELLED`로 변경되며, 재시도 실패 시 상태는 `FINAL_COMPENSATION_FAILED`로 업데이트되고 Slack 알림이 전송됩니다.

- **보정(조정) 프로세스:**  
  시스템 시작 시와 스프링 스케줄러를 통해 5분 이상 `FINAL_COMPLETED` 상태에 도달하지 않은 결제(Payment)를 대상으로 실제 PG 결제 내역 조회 API(모킹)를 통해 결제 상태를 검증하고 조정합니다.

## 주요 기능

- **Spring Boot 애플리케이션**
    - Spring Retry를 사용하여 자동 재시도(지터 백오프 지원) 기능 구현
    - Spring Scheduling을 통해 미완료 결제 조정 작업 수행
- **조건부 업데이트**를 통한 동시성 제어 및 `EnrollmentCount` 관리
-  **Payment** 상태를 트랜잭션별로 변경
- **보상 메커니즘:**  
  보상 이벤트와 리스너를 사용하여 각 트랜잭션 단계에서 실패 발생 시 보상 처리 수행
- **보상 알림:**  
  보상 로직 재시도 모두 실패하면 로그와 Slack(모킹) 알림을 통해 운영자에게 문제를 통지

## 아키텍처

애플리케이션은 아래와 같은 핵심 모듈로 구성됩니다.

### 엔티티 (Entities)
- **`EnrollmentCount`**  
  낙관적 락을 적용하여 현재 수강 인원을 관리하는 테이블
- **`Enrollment`**  
  강의 구매 기록을 저장하는 테이블 (결제 상태 정보는 별도 관리)
- **`Payment`**  
  결제 상태 및 결제 관련 내역(예: 상태, 생성 시간 등)을 관리하는 테이블

### Repository
- **`EnrollmentCountRepository`**  
  조건부 증감 업데이트 메서드를 포함
- **`EnrollmentRepository`**  
  Enrollment 레코드에 대한 기본 CRUD 제공
- **`PaymentRepository`**  
  조건부 상태 업데이트 및 생성 시간, 상태 기준 조회 메서드를 제공

### Application (Service / Orchestration / Event)
- **서비스 (Service)**
    - **`EnrollmentService`**  
      각 트랜잭션 단계를 구현하며,
        - 트랜잭션 1: 수강 인원 증가와 `Payment` 상태를 `CREATED` → `COUNT_UPDATED`로 변경
        - 트랜잭션 2: PG 결제(모킹) 처리 후 `Payment` 상태를 `COUNT_UPDATED` → `PAYMENT_PROCESSED`로 변경
        - 트랜잭션 3: Enrollment 생성과 함께 `Payment` 상태를 `PAYMENT_PROCESSED` → `FINAL_COMPLETED`로 변경
    - **`CompensationService`**  
      보상 이벤트(`PaymentCancellationEvent`, `FinalizationCompensationEvent`)를 처리하며,  
      보상 로직 성공 시 상태를 `CANCELLED`로, 재시도 실패 시 각각 `CANCELLATION_FAILED` 또는 `FINAL_COMPENSATION_FAILED`로 업데이트하고 Slack 알림(모킹)을 전송
    - **`PaymentAdjustmentService`**  
      스케줄러 및 애플리케이션 시작 시 실행되어 미완료 Payment를 검증하고 조정
- **오케스트레이션**
    - **`LecturePaymentOrchestration`**  
      전체 결제 프로세스를 조율하며, 각 단계 실패 시 적절한 보상 이벤트를 발행
- **이벤트 (Event)**
    - **`PaymentCancellationEvent`**: 트랜잭션 2 보상 이벤트
    - **`FinalizationCompensationEvent`**: 트랜잭션 3 보상 이벤트
    - **`PaymentCompensationListener`**: 보상 이벤트를 수신하여 `CompensationService`를 호출

### Web Layer
- **컨트롤러 (Controller)**
    - **`PaymentController`**: 결제 요청을 처리하는 REST API 제공
    - **DTO (`PaymentRequest`)**: 결제 요청 데이터를 전달하기 위한 객체
- **외부 연동 (External)**
    - **`PgApiExecutorService`**: 외부 PG API 호출을 담당 (모킹 처리)
    - **DTO (`PaymentResponse`)**: 외부 PG API 응답 데이터를 전달하기 위한 객체

## 코드 구조

```
payment/
├── application
│   ├── event
│   │   ├── FinalizationCompensationEvent.java
│   │   ├── PaymentCancellationEvent.java
│   │   └── PaymentCompensationListener.java
│   ├── exception
│   │   └── BusinessException.java
│   ├── orchestration
│   │   └── LecturePaymentOrchestration.java
│   └── service
│       ├── CompensationService.java
│       ├── EnrollmentService.java
│       └── PaymentAdjustmentService.java
├── entity
│   ├── Enrollment.java
│   ├── EnrollmentCount.java
│   └── Payment.java
├── repository
│   ├── EnrollmentCountRepository.java
│   ├── EnrollmentRepository.java
│   └── PaymentRepository.java
└── web
    ├── controller
    │   ├── PaymentController.java
    │   └── dto
    │       └── PaymentRequest.java
    └── external
        ├── PgApiExecutorService.java
        └── dto
            └── PaymentResponse.java
```
