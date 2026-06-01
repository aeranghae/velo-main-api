package cloud.velo.main.dto.response;

import lombok.Getter;

@Getter
public class StorageUsageResponse {
    private final long usageBytes;
    private final String usageMB;

    public StorageUsageResponse(long usageBytes) {
        this.usageBytes = usageBytes;
        this.usageMB = String.format("%.2f", (double) usageBytes / (1024 * 1024));
    }
}