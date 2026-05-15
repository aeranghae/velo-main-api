package cloud.aeranghae.main.util.template;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

public abstract class BaseTemplateInitializer implements TemplateInitializer {

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