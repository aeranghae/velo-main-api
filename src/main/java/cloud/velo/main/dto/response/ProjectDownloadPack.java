package cloud.velo.main.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.ContentDisposition;

@Getter
@AllArgsConstructor
public class ProjectDownloadPack {
    private byte[] zipData;
    private ContentDisposition contentDisposition;
}