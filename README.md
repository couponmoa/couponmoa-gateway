# 🚪 couponmoa-gateway

## 📌 개요

이 서버는 Couponmoa 프로젝트의 **API Gateway 역할**을 담당합니다.  
클라이언트 요청을 각 마이크로서비스로 **라우팅**하고, **JWT 인증 필터링**을 통해 인증되지 않은 요청을 차단합니다.

---

## 🔧 주요 기능

- **Spring Cloud Gateway 기반 라우팅**
- **JWT 기반 인증 및 인가 필터**
- 서비스 경로 매핑: 클라이언트 요청을 내부 서비스로 전달

---

## 📂 라우팅 예시

| 경로 | 대상 서비스 |
|------|-------------|
| `/api/v1/users/**` | couponmoa-user |
| `/api/v1/stores/**` | couponmoa-store |
| `/api/v1/coupons/**` | couponmoa-coupon |
| `/api/v1/notifications/**` | couponmoa-notification |

---

## 🧰 기술 스택

- Java 17
- Spring Boot 3.x
- Spring Cloud Gateway
- JWT (Json Web Token)
- Gradle



