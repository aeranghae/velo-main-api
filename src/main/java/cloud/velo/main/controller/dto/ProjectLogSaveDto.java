package cloud.velo.main.controller.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProjectLogSaveDto {
    private String uuid;
    private String logLevel;
    private String message;
    private String status;
}