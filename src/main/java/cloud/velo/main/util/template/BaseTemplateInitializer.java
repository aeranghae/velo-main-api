package cloud.velo.main.util.template;

import cloud.velo.main.controller.dto.ProjectCreateRequestDto;
import cloud.velo.main.util.template.initializer.license.LicenseGenerator;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

public abstract class BaseTemplateInitializer implements TemplateInitializer {

    @Autowired
    private LicenseGenerator licenseGenerator;

    // final로 막아서 각 initializer가 override 못하게
    @Override
    public final void initialize(Path rootPath, ProjectCreateRequestDto dto) {
        initTemplate(rootPath, dto); // 각 initializer 구현체가 실행
        writeLicense(rootPath, dto); // 라이선스는 항상 자동 생성
    }

    // initialize() 대신 이걸 각 initializer에서 구현
    protected abstract void initTemplate(Path rootPath, ProjectCreateRequestDto dto);

    protected void writeLicense(Path rootPath, ProjectCreateRequestDto dto) {
        String content = licenseGenerator.generate(dto.getLicense(), dto.getProjectName());
        if (!content.isBlank()) {
            writeFile(rootPath.resolve("LICENSE"), content);
        }
    }

    protected void createDirectories(Path rootPath, List<String> paths) {
        paths.forEach(path -> {
            try {
                Files.createDirectories(rootPath.resolve(path));
            } catch (IOException e) {
                throw new RuntimeException("디렉토리 생성 실패: " + path, e);
            }
        });
    }

    protected void writeFile(Path filePath, String content) {
        try {
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, content, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new RuntimeException("파일 생성 실패: " + filePath, e);
        }
    }

    protected String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return Character.toUpperCase(str.charAt(0)) + str.substring(1).toLowerCase();
    }
}