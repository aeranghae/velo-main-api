package cloud.velo.main.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.ContentDisposition;

@Getter
@AllArgsConstructor
public class ProjectDownloadPackResponse {
    private byte[] zipData;
    private ContentDisposition contentDisposition;
}