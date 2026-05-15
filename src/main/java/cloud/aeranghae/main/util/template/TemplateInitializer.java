package cloud.aeranghae.main.util.template;

import cloud.aeranghae.main.controller.dto.ProjectCreateRequestDto;

import java.nio.file.Path;

public interface TemplateInitializer {
    String getSupportedFramework();
    void initialize(Path rootPath, ProjectCreateRequestDto dto);
}