package cloud.velo.main.event;

import java.util.concurrent.atomic.AtomicBoolean;

public record ProjectDeleteVerificationEvent(String uuid, AtomicBoolean isGenerating) {
    // 기존 단건 삭제 로직들과의 호환성을 위해 기본 생성자 오버로딩
    public ProjectDeleteVerificationEvent(String uuid) {
        this(uuid, new AtomicBoolean(false));
    }
}