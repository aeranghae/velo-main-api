package cloud.velo.main.domain;

import lombok.Getter;

@Getter
public enum ProjectStatus {
    CREATED("프로젝트 생성 완료"),
    INIT("시스템 초기화 및 격리 구역 준비 중..."),

    // 2. 인프라 공간 및 기본 구조 빌드 (10% ~ 20%)
    PROVISIONING("개발 환경 인프라 및 작업 공간 구축 중..."),

    // 3. 메타데이터 및 정책 설정 (20% ~ 30%)
    CONFIGURING("오픈소스 라이선스 및 프로젝트 환경 설정 중..."),

    // 4. AI 자율 공정 핵심 단계 (30% ~ 100%)
    ANALYZING("AI가 요구사항을 분석하고 설계하는 중..."),
    CODING("AI 에이전트 소스 코드 작성 중..."),
    EXECUTING("샌드박스 내부 스크립트(빌드) 실행 중..."),

    // 5. 종료 상태
    COMPLETED("자율 코딩 공정 최종 완료"),
    FAILED("공정 중단 (에러 발생)");
    private final String description; // 화면에 띄워줄 한글 이름

    ProjectStatus(String description) {
        this.description = description;
    }
}
