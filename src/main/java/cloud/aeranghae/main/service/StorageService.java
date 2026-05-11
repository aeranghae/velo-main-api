package cloud.aeranghae.main.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class StorageService {

    @Value("${aeranghae.storage.path}")
    private String baseStoragePath;

    public void createUserDirectory(String userIdentifier) {
        // userIdentifier는 유저의 ID(PK)로 사용합니다.
        Path path = Paths.get(baseStoragePath, userIdentifier);

        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                // 필요하다면 하위에 기본 폴더 구조(예: /projects, /logs)를 더 만들 수 있움
                System.out.println("디렉토리 생성 완료: " + path.toAbsolutePath());
            }
        } catch (IOException e) {
            throw new RuntimeException("유저 디렉토리 생성 실패: " + userIdentifier, e);
        }
    }
}