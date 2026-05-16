package cloud.velo.main.util.template;

import cloud.velo.main.controller.dto.ProjectCreateRequestDto;

import java.nio.file.Path;

public interface TemplateInitializer {
    String getSupportedFramework();
    void initialize(Path rootPath, ProjectCreateRequestDto dto);
}