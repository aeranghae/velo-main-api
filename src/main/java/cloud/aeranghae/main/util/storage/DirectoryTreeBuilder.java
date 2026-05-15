package cloud.aeranghae.main.util.storage;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

@Component
public class DirectoryTreeBuilder {

    public String build(Path rootPath) {
        StringBuilder sb = new StringBuilder();
        sb.append(rootPath.getFileName()).append("/\n");
        buildTree(rootPath, "", sb);
        return sb.toString();
    }

    private void buildTree(Path path, String prefix, StringBuilder sb) {
        try {
            List<Path> children = Files.list(path)
                    .sorted(Comparator
                            .comparing((Path p) -> Files.isRegularFile(p)) // 디렉토리 먼저
                            .thenComparing(p -> p.getFileName().toString()))
                    .toList();

            for (int i = 0; i < children.size(); i++) {
                Path child = children.get(i);
                boolean isLast = (i == children.size() - 1);

                String connector = isLast ? "└── " : "├── ";
                String childPrefix = isLast ? prefix + "    " : prefix + "│   ";

                sb.append(prefix).append(connector).append(child.getFileName());

                if (Files.isDirectory(child)) {
                    sb.append("/\n");
                    buildTree(child, childPrefix, sb);
                } else {
                    sb.append("\n");
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("트리 생성 실패: " + path, e);
        }
    }
}