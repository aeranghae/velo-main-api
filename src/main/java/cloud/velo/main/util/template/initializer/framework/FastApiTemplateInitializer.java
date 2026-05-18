package cloud.velo.main.util.template.initializer.framework;

import cloud.velo.main.controller.dto.ProjectCreateRequestDto;
import cloud.velo.main.util.template.BaseTemplateInitializer;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

@Component
public class FastApiTemplateInitializer extends BaseTemplateInitializer {

    @Override
    public String getSupportedFramework() { return "fastapi"; }

    @Override
    public void initTemplate(Path rootPath, ProjectCreateRequestDto dto) {
        String projectName = dto.getProjectName().toLowerCase();

        createDirectories(rootPath, List.of(
                "app/api/v1/endpoints",
                "app/core",
                "app/models",
                "app/schemas",
                "app/services",
                "app/repositories",
                "tests"
        ));

        writeFile(rootPath.resolve("app/main.py"), mainPy(projectName));
        writeFile(rootPath.resolve("app/core/config.py"), configPy(projectName));
        writeFile(rootPath.resolve("app/api/v1/router.py"), routerPy());
        writeFile(rootPath.resolve("app/__init__.py"), "");
        writeFile(rootPath.resolve("app/api/__init__.py"), "");
        writeFile(rootPath.resolve("app/api/v1/__init__.py"), "");
        writeFile(rootPath.resolve("app/api/v1/endpoints/__init__.py"), "");
        writeFile(rootPath.resolve("app/models/__init__.py"), "");
        writeFile(rootPath.resolve("app/schemas/__init__.py"), "");
        writeFile(rootPath.resolve("app/services/__init__.py"), "");
        writeFile(rootPath.resolve("requirements.txt"), requirements());
        writeFile(rootPath.resolve(".env.example"), envExample(projectName));
        writeFile(rootPath.resolve(".gitignore"), fastApiGitignore());
        writeFile(rootPath.resolve("README.md"), readme(projectName, dto.getLicense()));
    }

    private String mainPy(String projectName) {
        return """
                from fastapi import FastAPI
                from fastapi.middleware.cors import CORSMiddleware
                from app.api.v1.router import api_router
                from app.core.config import settings
                
                app = FastAPI(
                    title="%s",
                    version="0.1.0",
                    openapi_url="/api/v1/openapi.json"
                )
                
                app.add_middleware(
                    CORSMiddleware,
                    allow_origins=settings.ALLOWED_ORIGINS,
                    allow_credentials=True,
                    allow_methods=["*"],
                    allow_headers=["*"],
                )
                
                app.include_router(api_router, prefix="/api/v1")
                """.formatted(projectName);
    }

    private String configPy(String projectName) {
        return """
                from pydantic_settings import BaseSettings
                from typing import List
                
                class Settings(BaseSettings):
                    APP_NAME: str = "%s"
                    DEBUG: bool = False
                    DATABASE_URL: str = "postgresql://user:password@localhost:5432/%s"
                    ALLOWED_ORIGINS: List[str] = ["http://localhost:3000"]
                    SECRET_KEY: str = "your-secret-key"
                
                    class Config:
                        env_file = ".env"
                
                settings = Settings()
                """.formatted(projectName, projectName);
    }

    private String routerPy() {
        return """
                from fastapi import APIRouter
                
                api_router = APIRouter()
                
                # 엔드포인트 추가 예시
                # from app.api.v1.endpoints import users
                # api_router.include_router(users.router, prefix="/users", tags=["users"])
                """;
    }

    private String requirements() {
        return """
                fastapi==0.115.0
                uvicorn[standard]==0.30.0
                sqlalchemy==2.0.36
                alembic==1.13.3
                pydantic==2.9.2
                pydantic-settings==2.6.0
                python-dotenv==1.0.1
                psycopg2-binary==2.9.10
                python-jose[cryptography]==3.3.0
                passlib[bcrypt]==1.7.4
                """;
    }

    private String envExample(String projectName) {
        return """
                APP_NAME=%s
                DEBUG=False
                DATABASE_URL=postgresql://user:password@localhost:5432/%s
                SECRET_KEY=your-secret-key
                ALLOWED_ORIGINS=["http://localhost:3000"]
                """.formatted(projectName, projectName);
    }

    private String fastApiGitignore() {
        return """
                __pycache__/
                *.py[cod]
                .env
                .venv/
                venv/
                .DS_Store
                *.egg-info/
                dist/
                .pytest_cache/
                """;
    }

    private String readme(String projectName, String license) {
        return """
                # %s
                
                ## Getting Started
                
                ### Prerequisites
                - Python 3.11+
                - PostgreSQL
                
                ### Run
```bash
                pip install -r requirements.txt
                uvicorn app.main:app --reload
```
                
                ## License
                %s
                """.formatted(projectName, license);
    }
}