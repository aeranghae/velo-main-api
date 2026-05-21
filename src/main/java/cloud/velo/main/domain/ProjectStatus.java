package cloud.velo.main.domain;

import lombok.Getter;

@Getter
public enum ProjectStatus {
    CREATED("프로젝트 생성 완료"), // API로 막 생성된 직후 대기 상태
    ANALYZING("요구사항 분석 및 환경 세팅 중..."), // 샌드박스 켜고 초기 프롬프트 던질 때
    CODING("AI 에이전트 소스 코드 작성 중..."), // 도구: WRITE_FILE, DELETE_FILE
    EXECUTING("컨테이너 내부 스크립트(빌드) 실행 중..."), // 도구: EXECUTE_CMD
    COMPLETED("자율 코딩 공정 최종 완료"), // 도구: FINISH
    FAILED("공정 중단 (에러 발생)"); // 에러, 타임아웃 발생 시


    private final String description; // 화면에 띄워줄 한글 이름

    ProjectStatus(String description) {
        this.description = description;
    }
}
