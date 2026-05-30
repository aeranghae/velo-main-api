package cloud.velo.main.util.template;

import cloud.velo.main.dto.request.ProjectCreateRequest;

import java.nio.file.Path;

public interface TemplateInitializer {
    String getSupportedFramework();
    void initialize(Path rootPath, ProjectCreateRequest dto);
}