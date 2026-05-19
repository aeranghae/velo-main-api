package cloud.velo.main.docker.dto;

import lombok.*;

public class AiModelMessage {

    // LLM이 스프링 비서에게 내리는 액션 명령 규격
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @ToString
    public static class Action {
        private String tool;        // "WRITE_FILE" 또는 "EXECUTE_CMD"
        private String path;        // 프레임워크별 파일 경로 (예: "app/main.py", "package.json")
        private String content;     // 소스코드 본문
        private String cmd;         // 실행할 쉘 명령 (예: "pip install -r requirements.txt", "npm run test")
        private String baseImage;   // 동적 환경 지정 (예: "python:3.11-slim", "node:20-alpine", "openjdk:21-slim")
    }

    // 스프링이 실행 후 LLM 두뇌에게 돌려주는 결과 피드백 규격
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @ToString
    public static class Observation {
        private String status;      // "SUCCESS" 또는 "ERROR"
        private int exitCode;       // 프로세스 종료 코드
        private String stdout;      // 표준 출력 내용
        private String stderr;      // 문법 에러, 컴파일 에러, 런타임 예외 등
    }
}