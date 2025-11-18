# couponpop-member-service

CouponPop 마이크로서비스 아키텍처의 **회원 관리(Member)** 및 **인증(Auth)** 도메인을 담당하는 서비스입니다.

---

## 1. 주요 역할

* **[회원 관리]** 회원(CUSTOMER, OWNER, ADMIN)의 CRUD 로직을 담당합니다.
* **[인증/인가]** 회원가입, 로그인, 로그아웃, 회원탈퇴 API를 제공합니다.
* **[토큰 관리]** 로그인 성공 시 JWT를 발급하며, 로그아웃/회원탈퇴 시 `couponpop-security-module`의 `TokenBlacklistService`를 호출하여 토큰을 블랙리스트 처리합니다.
* **[서비스 연동]** OpenFeign을 사용하여 로그아웃/회원탈퇴 시 `notification-service`에 FCM 토큰 만료를 요청합니다.
* **[데이터베이스]** Flyway를 통해 DB 스키마(`members` 테이블)를 관리하며, Master/Slave DB 이중화를 통한 읽기/쓰기 분리(Replication)가 적용되어 있습니다.

## 2. 기술 스택

* **Language**: Java 17
* **Framework**: Spring Boot 3.x, Spring Data JPA, Spring Security
* **Database**: MySQL, Flyway, H2 (테스트용)
* **CI/CD**: Jenkins, Docker, SonarQube, Jacoco
* **Monitoring**: Micrometer (Prometheus)
* **common module**: `couponpop-core-module`, `couponpop-security-module`
* **Internal Service Client**: Spring Cloud OpenFeign

## 3. API 엔드포인트

### Auth API (`/api/v1/auth`)
* `POST /signup`: 회원가입 (CUSTOMER, OWNER만 가능)
* `POST /login`: 로그인 (Access Token 발급)
* `POST /logout`: 로그아웃 (JWT 블랙리스트 처리 및 FCM 토큰 만료 요청)
* `DELETE /withdraw`: 회원탈퇴 (Soft delete, JWT 블랙리스트 처리 및 FCM 토큰 만료 요청)

### Member API (`/api/v1/members`)
* `GET /me`: 내 프로필 조회
* `PUT /me`: 내 프로필 수정 (사용자 이름, 비밀번호, 전화번호)

## 4. 외부 마이크로서비스 및 리소스

* **[Downstream Services]**
    * `notification-service`: 로그아웃/회원탈퇴 시 FCM 토큰 만료를 요청합니다.
* **[Database]**
    * `MySQL (Master/Slave)`: 회원 정보 저장 및 조회
    * `Redis`: `couponpop-security-module`을 통해 JWT 블랙리스트 관리에 사용됩니다.

## 5. 환경 변수 및 설정

이 서비스를 실행하기 위해 필요한 환경 변수 목록입니다. (`.env.example` 참고)

| 변수명 | 설명 | 예시 |
| :--- | :--- | :--- |
| `DB_MASTER_URL` | Master DB (쓰기) JDBC URL | `(AWS Parameter Store)` |
| `DB_SLAVE_URL` | Slave DB (읽기) JDBC URL | `(AWS Parameter Store)` |
| `DB_USERNAME` | DB 사용자명 | `(AWS Parameter Store)` |
| `DB_PASSWORD` | DB 비밀번호 | `(AWS Parameter Store)` |
| `REDIS_HOST` | Redis 호스트 (JWT 블랙리스트용) | `(AWS Parameter Store)` |
| `REDIS_PORT` | Redis 포트 | `(AWS Parameter Store)` |
| `JWT_SECRET_KEY` | JWT 서명 발급/검증용 비밀 키 | `(AWS Parameter Store)` |
| `GITHUB_ACTOR` | GitHub Packages Read용 ID | `(Jenkins Credential)` |
| `GITHUB_TOKEN` | GitHub Packages Read용 PAT | `(Jenkins Credential)` |
| `client.notification-service.url`| 알림 서비스 내부 DNS | `http://notification.couponpop.internal:8080` |

## 6. 로컬 개발 실행 방법

이 서비스는 로컬에서 실행하기 위해 **JDK 17**, **MySQL**, **Redis**가 필요합니다.
또한 `couponpop-security-module` 등 비공개 GitHub Packages 의존성을 다운로드하기 위해 GitHub PAT(Personal Access Token)가 필요합니다.

1.  **(필수) GitHub PAT 발급**:
    * GitHub에서 `read:packages` 스코프를 가진 PAT를 발급받습니다.

2.  **(필수) 인프라 실행**:
    * 로컬에 MySQL(Master/Slave) 및 Redis를 실행합니다.
    * (권장) 프로젝트 루트의 `local-docker-infra` 내 `docker-compose.db.replica.yml` 및 `docker-compose.yml`을 활용하세요.

3.  **(필수) `.env` 파일 생성**:
    * 이 `member-service` 프로젝트 루트에 `.env` 파일을 생성하고, `build.gradle`과 `application.yml`, `application-local.yml`이 참조할 변수들을 입력합니다.

    ```dotenv
    # build.gradle이 사용할 GitHub Packages 인증 정보
    GITHUB_ACTOR=your-github-username
    GITHUB_TOKEN=your-github-pat-token

    # application-local.yml이 사용할 DB/Redis/JWT 정보
    DB_MASTER_URL=jdbc:mysql://localhost:3307/member_db
    DB_SLAVE_URL=jdbc:mysql://localhost:3317/member_db
    DB_USERNAME=root
    DB_PASSWORD=1234
    REDIS_HOST=localhost
    REDIS_PORT=6379
    JWT_SECRET_KEY=local-test-jwt-secret-key-long-enough-for-hs256
    ```

4.  **(필수) DB 마이그레이션**:
    * `application-local.yml`에 `spring.flyway.enabled=true`가 설정되어 있는지 확인합니다.
    * 애플리케이션을 실행하면 Flyway가 자동으로 `V1`, `V2`, `V3` 스크립트를 실행하여 `members` 테이블을 Master DB에 생성합니다.

5.  **애플리케이션 실행**:
    * IDE의 실행 설정(Run Configuration)에서 Active Profile을 `local`로 설정하여 실행합니다.
    * (또는) 터미널에서 Gradle로 직접 실행합니다 (Port: 8081):
    ```bash
    ./gradlew bootRun --args='--spring.profiles.active=local'
    ```

6.  **(선택) 연동 테스트**:
    * `api-gateway`, `notification-service` (Port 8084)를 함께 실행하면, 로그아웃/회원탈퇴 시 OpenFeign을 통한 FCM 토큰 만료 요청이 정상적으로 동작하는지 확인할 수 있습니다.

## 7. 운영 시 참고 사항

* **[AWS Parameter Store]** 운영(`prod`) 환경의 모든 민감 정보(DB, Redis, JWT Key 등)는 `build.gradle`의 `spring-cloud-aws-starter-parameter-store` 의존성을 통해 AWS Parameter Store에서 주입받습니다.
* **[DB Replication]** `DataSourceConfig`에 따라 Master/Slave DB가 분리되어 있습니다. `@Transactional(readOnly = true)`가 붙은 서비스 로직은 **Slave DB**로, 쓰기 트랜잭션은 **Master DB**로 자동 라우팅됩니다.
* **[Redis 의존성]** 이 서비스는 `couponpop-security-module`을 통해 Redis에 강하게 의존합니다. Redis는 로그아웃/회원탈퇴 시 JWT를 무효화하는 **블랙리스트 저장소**로 사용됩니다. Redis 장애 시 토큰 무효화가 지연될 수 있습니다.
* **[모니터링]** `/actuator/prometheus` 엔드포인트를 통해 서비스의 상세 메트릭(JVM, DB Pool, API Latency 등)이 노출됩니다.
