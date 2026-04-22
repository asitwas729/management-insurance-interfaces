# InterfaceHub MVP

보험사 금융 IT 인터페이스 통합관리시스템의 Spring Boot MVP입니다.

## 현재 구현 범위

- 인터페이스 등록/목록/상세 조회
- 인터페이스 설정 버전 등록
- 설정 버전 publish
- REST 인터페이스 수동 실행
- 실행 성공/실패 이력 저장
- 실행 이력 조회
- 재처리 요청/승인/실행
- 감사 로그 자동 기록(PUBLISH_CONFIG, REQUEST_RETRY, APPROVE_RETRY, EXECUTE_RETRY)
- 외부 호출과 DB 트랜잭션 분리(이력 생성/결과 업데이트는 짧은 트랜잭션)
- request/response payload 저장 전 기본 마스킹(주민번호, 카드번호, 휴대폰)
- 공통 실행 라우터 `ExecutorRouter`
- REST 어댑터 `RestInterfaceExecutor`
- 공통 오류 응답
- H2 기반 개발 DB

## 기술 스택

- Java 17
- Spring Boot 3.3.5
- Spring Web MVC
- Spring WebFlux `WebClient`
- Spring Data JPA
- H2 Database
- JUnit 5

> 로컬 환경의 JDK가 Java 17이라 `pom.xml`도 17로 설정했습니다. Java 21을 설치한 뒤에는 `pom.xml`의 `java.version`을 `21`로 변경하면 됩니다.

## 실행

```bash
mvn spring-boot:run
```

기본 포트:

```text
http://localhost:8080
```

H2 Console:

```text
http://localhost:8080/h2-console
JDBC URL: jdbc:h2:mem:interfacehub
User: sa
Password: 비워둠
```

## 테스트

```bash
mvn test
```

## API 예시

### 인터페이스 등록

```http
POST /api/v1/interfaces
Content-Type: application/json
```

```json
{
  "interfaceCode": "FSS_POLICY_REPORT",
  "name": "금감원 보험계약 보고",
  "protocolType": "REST",
  "ownerTeam": "PolicyCore",
  "slaMillis": 3000
}
```

### 설정 등록

```http
POST /api/v1/interfaces/FSS_POLICY_REPORT/configs
Content-Type: application/json
```

```json
{
  "endpoint": "https://external.example.com/report",
  "authType": "NONE",
  "headers": {},
  "timeoutMillis": 3000
}
```

### 설정 publish

```http
POST /api/v1/interfaces/FSS_POLICY_REPORT/configs/1/publish
```

### 수동 실행

```http
POST /api/v1/interfaces/FSS_POLICY_REPORT/execute
Content-Type: application/json
```

```json
{
  "idempotencyKey": "FSS-POLICY-0001",
  "payload": {
    "policyNo": "P202604220001",
    "eventType": "NEW_CONTRACT"
  }
}
```

같은 `idempotencyKey`로 같은 실행 API를 다시 호출하면 `409 DUPLICATE_REQUEST`를 반환합니다.

### 실행 이력 조회

```http
GET /api/v1/interfaces/FSS_POLICY_REPORT/histories?page=0&size=20
```

### 재처리 요청

```http
POST /api/v1/interfaces/FSS_POLICY_REPORT/retries
Content-Type: application/json
```

```json
{
  "originalExecutionId": "b8fdc9f4-9a20-4e1f-8c47-6d55e4d18111",
  "requester": "operator1"
}
```

### 재처리 승인

```http
POST /api/v1/retries/{retryTaskId}/approve
Content-Type: application/json
```

```json
{
  "approver": "manager1"
}
```

### 재처리 실행

```http
POST /api/v1/retries/{retryTaskId}/execute
```

## 다음 단계

1. Resilience4j 기반 timeout/retry/circuit breaker 적용
2. Idempotency Key 기반 중복 실행 방지
3. AuditLog 추가
4. 재처리 요청/승인 흐름 추가
5. PostgreSQL + Flyway 전환
