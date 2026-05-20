package cloud.velo.main.domain;

import lombok.Getter;

@Getter
public enum ProjectStatus {
    CREATED("프로젝트 생성 완료",10),       // 프로젝트 막 생성됨 defulat 0~30%
    ANALYZING("Ai가요구사항을 분석 하고있습니다.", 30),
    GENERATING("Ai 소스 코드 생성중.." , 50),    // AI가 코드 생성중 30 ~ 70%
    COMPLETED("코드 생성 완료",100),     // 작업 완료  70 ~ 100%
    FAILED("코드 생성 실패", -1);        // 작업 중 에러 발생  // STOP%


    private final String description; // 화면에 띄워줄 한글 이름
    private final int progress;       // 프론트엔드 프로그레스 바 수치

    ProjectStatus(String description, int progress) {
        this.description = description;
        this.progress = progress;
    }
}
