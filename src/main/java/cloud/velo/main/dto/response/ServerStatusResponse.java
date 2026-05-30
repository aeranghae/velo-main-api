package cloud.velo.main.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ServerStatusResponse {
    private String status;       // UP / DOWN
    private long uptime;         // 서버 가동 시간 (ms)
    private double cpuUsage;     // CPU 사용률 (%)
    private long totalMemory;    // 전체 메모리 (Bytes)
    private long freeMemory;     // 사용 가능한 여유 메모리 (Bytes)
    private long usedMemory;     // 현재 사용 중인 메모리 (Bytes)
}