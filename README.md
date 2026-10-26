# AutoStudy

PDF 학습 자료를 업로드하면 AI가 내용을 분석해 퀴즈를 만들어 주는 학습 웹 서비스입니다.
학습 기록 관리와 팀 단위 공유 공간을 지원합니다.


## 주요 기능

- **AI 퀴즈 생성** : PDF에서 텍스트를 추출해 AI 모델이 퀴즈를 JSON 형식으로 생성
- **학습 기록** : 풀었던 퀴즈와 결과를 기록하고 다시 확인
- **팀 스페이스** : 팀을 만들어 학습 자료와 퀴즈 결과를 함께 공유
- **회원 관리** : 회원가입 · 로그인, 구글 계정 로그인, 이메일을 통한 비밀번호 재설정



## 기술 스택

| 구분 | 사용 기술 |
| --- | --- |
| Backend | Java 17, Spring Boot 4, Spring Security, Spring Data JPA (Hibernate) |
| Frontend | Thymeleaf, HTML, CSS, JavaScript |
| Database | MySQL 8 |
| AI | OpenRouter API (Gemma 4 31B 메인, 실패 시 폴백 모델로 재시도) |
| 기타 | Apache PDFBox 3, Google OAuth 2.0, Gmail SMTP, JWT |



## 구현 포인트

**AI 응답 안정성**
메인 모델이 응답하지 못하면 같은 입력으로 폴백 모델을 호출하도록 구성해, 한 모델의 장애가 서비스 중단으로 이어지지 않게 했습니다.

**인증**
BCrypt로 비밀번호를 암호화하고, Spring Security 기반 로그인에 구글 OAuth 2.0 로그인과 JWT(Access · Refresh) 로그인 유지를 함께 적용했습니다. 비밀번호를 잊은 사용자는 Gmail SMTP로 발송되는 메일을 통해 재설정할 수 있습니다.

**상황별 예외 처리**

| 상황 | 응답 코드 |
| --- | --- |
| 허용되지 않은 확장자 업로드 (.exe 등) | 400 |
| 100MB 초과 파일 업로드 | 413 |
| 이미지 기반 PDF 등 텍스트 추출 실패 | 422 |
| AI 응답 JSON 파싱 실패 | 500 |
| OpenRouter 30초 무응답 | 503 |

**설계 단순화**
초기 설계의 복잡한 다중 테이블 구조를 사용자 · 팀 · 학습 콘텐츠 중심의 핵심 테이블로 단순화했습니다.

**보안 정보 분리**
DB 비밀번호, API 키, OAuth 시크릿, JWT 키는 코드에 두지 않고 환경변수(`.env`)로 분리했습니다.


## 프로젝트 구조

```
backend/backend/src/main
├── java/com/example/backend
│   ├── config        # Security, JWT 필터, OAuth2, 메일, Web 설정
│   ├── controller    # 퀴즈, 기록, 팀, 인증, 메일 API
│   └── service       # AI 연동, JWT, 메일, 엔티티 · 리포지토리
└── resources
    ├── templates     # 로그인, 회원가입, 대시보드, 퀴즈, 기록, 팀 화면
    └── static        # CSS, JavaScript
```



## 실행 방법

**1. 데이터베이스 생성**

```sql
CREATE DATABASE autostudy;
```

**2. 환경변수 파일 작성**

`backend/backend` 폴더에 `.env` 파일을 만들고 값을 입력합니다.

```
DB_PASSWORD=MySQL root 비밀번호
OPENROUTER_API_KEY=OpenRouter API 키
JWT_SECRET=32자 이상의 랜덤 문자열
GOOGLE_CLIENT_ID=구글 OAuth 클라이언트 ID
GOOGLE_CLIENT_SECRET=구글 OAuth 클라이언트 보안 비밀
```

**3. 실행**

```bash
cd backend/backend
./mvnw spring-boot:run
```

브라우저에서 `http://localhost:8080` 으로 접속합니다.

<br>

## 개발자

**김다빈** · 대전보건대학교 컴퓨터정보과
GitHub [@dabin1213](https://github.com/dabin1213)
