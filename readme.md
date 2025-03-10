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

## 아키텍처

애플리케이션은 아래와 같은 핵심 모듈로 구성됩니다.

### 엔티티 (Entities)
- **`EnrollmentCount`**  
  조건부 업데이트의 베타락으로 현재 수강 인원을 관리하는 테이블
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

## 코드구조
```
payment/
├── application
│   ├── event
│   │   ├── FinalizationCompensationEvent.java      // 트랜잭션 3 보상 이벤트(최종 결제 DB 반영 실패 시) 정보를 담은 이벤트 클래스
│   │   ├── PaymentCancellationEvent.java           // 트랜잭션 2 보상 이벤트(PG API 호출 실패 시) 정보를 담은 이벤트 클래스
│   │   └── PaymentCompensationListener.java         // 보상 이벤트를 수신하여 CompensationService를 호출하는 리스너
│   ├── exception
│   │   └── BusinessException.java                   // 비즈니스 로직 예외를 처리하기 위한 커스텀 예외 클래스
│   ├── orchestration
│   │   └── LecturePaymentOrchestration.java         // 전체 결제 프로세스를 오케스트레이션하며 단계별 실행 및 보상 이벤트 발행을 담당
│   └── service
│       ├── CompensationService.java                 // 보상 로직(예: 보상 트랜잭션 및 fallback/recovery 처리)을 수행하는 서비스
│       ├── EnrollmentService.java                   // 동시성 제어, PG API 호출, 최종 DB 반영 등 결제 프로세스의 각 트랜잭션 단계를 처리하는 서비스
│       └── PaymentAdjustmentService.java            // 미완료 결제에 대해 주기적으로 조정(보정) 작업을 수행하는 서비스 (스케줄러 및 애플리케이션 시작 시 실행)
├── entity
│   ├── Enrollment.java                              // 강의 수강(구매) 기록을 저장하는 엔티티 (결제 상태 정보는 별도의 Payment 엔티티에서 관리)
│   ├── EnrollmentCount.java                         // 강의별 현재 수강 인원과 최대 정원을 관리하는 엔티티 (낙관적 락 적용)
│   └── Payment.java                                 // 결제 내역 및 결제 상태를 관리하는 엔티티
├── repository
│   ├── EnrollmentCountRepository.java             // EnrollmentCount 엔티티에 대한 CRUD 및 조건부 업데이트 쿼리 제공
│   ├── EnrollmentRepository.java                  // Enrollment 엔티티에 대한 CRUD 기능 제공
│   └── PaymentRepository.java                       // Payment 엔티티에 대한 CRUD 및 조건부 상태 업데이트 쿼리 제공
└── web
    ├── controller
    │   ├── PaymentController.java                   // 결제 API 요청을 처리하고, 오케스트레이션 서비스를 호출하여 결제 프로세스를 진행하는 REST 컨트롤러
    │   └── dto
    │       └── PaymentRequest.java                  // 결제 요청 시 클라이언트에서 전달하는 데이터를 담은 DTO 클래스
    └── external
        ├── PgApiExecutorService.java                // 외부 PG API와의 연동(모킹 처리 포함)을 담당하는 서비스 인터페이스 및 구현 클래스
        └── dto
            └── PaymentResponse.java                 // 외부 PG API 호출 응답 데이터를 담은 DTO 클래스
```
