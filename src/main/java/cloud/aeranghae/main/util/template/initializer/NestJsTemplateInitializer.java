package cloud.aeranghae.main.util.template.initializer;

import cloud.aeranghae.main.controller.dto.ProjectCreateRequestDto;
import cloud.aeranghae.main.util.template.BaseTemplateInitializer;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

import static java.nio.file.Files.createDirectories;

@Component
public class NestJsTemplateInitializer extends BaseTemplateInitializer {

    @Override
    public String getSupportedFramework() { return "nestjs"; }

    @Override
    public void initialize(Path rootPath, ProjectCreateRequestDto dto) {
        String projectName = dto.getProjectName().toLowerCase();

        createDirectories(rootPath, List.of(
                "src/common/filters",
                "src/common/guards",
                "src/common/interceptors",
                "src/common/decorators",
                "src/config",
                "test"
        ));

        writeFile(rootPath.resolve("src/main.ts"), mainTs(projectName));
        writeFile(rootPath.resolve("src/app.module.ts"), appModuleTs());
        writeFile(rootPath.resolve("src/app.controller.ts"), appControllerTs());
        writeFile(rootPath.resolve("src/app.service.ts"), appServiceTs());
        writeFile(rootPath.resolve("package.json"), packageJson(projectName));
        writeFile(rootPath.resolve("tsconfig.json"), tsconfig());
        writeFile(rootPath.resolve(".env.example"), envExample(projectName));
        writeFile(rootPath.resolve(".gitignore"), nestGitignore());
        writeFile(rootPath.resolve("README.md"), readme(projectName, dto.getLicense()));
    }

    private String mainTs(String projectName) {
        return """
                import { NestFactory } from '@nestjs/core';
                import { AppModule } from './app.module';
                import { ValidationPipe } from '@nestjs/common';
                import { DocumentBuilder, SwaggerModule } from '@nestjs/swagger';
                
                async function bootstrap() {
                  const app = await NestFactory.create(AppModule);
                
                  app.useGlobalPipes(new ValidationPipe({ whitelist: true, transform: true }));
                  app.enableCors();
                  app.setGlobalPrefix('api/v1');
                
                  const config = new DocumentBuilder()
                    .setTitle('%s')
                    .setVersion('1.0')
                    .addBearerAuth()
                    .build();
                  const document = SwaggerModule.createDocument(app, config);
                  SwaggerModule.setup('docs', app, document);
                
                  await app.listen(process.env.PORT ?? 3000);
                }
                bootstrap();
                """.formatted(capitalize(projectName));
    }

    private String appModuleTs() {
        return """
                import { Module } from '@nestjs/common';
                import { ConfigModule } from '@nestjs/config';
                import { AppController } from './app.controller';
                import { AppService } from './app.service';
                
                @Module({
                  imports: [
                    ConfigModule.forRoot({ isGlobal: true }),
                  ],
                  controllers: [AppController],
                  providers: [AppService],
                })
                export class AppModule {}
                """;
    }

    private String appControllerTs() {
        return """
                import { Controller, Get } from '@nestjs/common';
                import { AppService } from './app.service';
                
                @Controller()
                export class AppController {
                  constructor(private readonly appService: AppService) {}
                
                  @Get('health')
                  healthCheck(): string {
                    return this.appService.healthCheck();
                  }
                }
                """;
    }

    private String appServiceTs() {
        return """
                import { Injectable } from '@nestjs/common';
                
                @Injectable()
                export class AppService {
                  healthCheck(): string {
                    return 'OK';
                  }
                }
                """;
    }

    private String packageJson(String projectName) {
        return """
                {
                  "name": "%s",
                  "version": "0.0.1",
                  "scripts": {
                    "build": "nest build",
                    "start": "nest start",
                    "start:dev": "nest start --watch",
                    "start:prod": "node dist/main"
                  },
                  "dependencies": {
                    "@nestjs/common": "^11.0.0",
                    "@nestjs/core": "^11.0.0",
                    "@nestjs/config": "^4.0.0",
                    "@nestjs/swagger": "^11.0.0",
                    "@nestjs/typeorm": "^11.0.0",
                    "typeorm": "^0.3.20",
                    "class-validator": "^0.14.0",
                    "class-transformer": "^0.5.1",
                    "reflect-metadata": "^0.2.0",
                    "rxjs": "^7.8.1"
                  },
                  "devDependencies": {
                    "@nestjs/cli": "^11.0.0",
                    "@types/node": "^22.0.0",
                    "typescript": "^5.0.0"
                  }
                }
                """.formatted(projectName);
    }

    private String tsconfig() {
        return """
                {
                  "compilerOptions": {
                    "module": "commonjs",
                    "declaration": true,
                    "removeComments": true,
                    "emitDecoratorMetadata": true,
                    "experimentalDecorators": true,
                    "allowSyntheticDefaultImports": true,
                    "target": "ES2021",
                    "sourceMap": true,
                    "outDir": "./dist",
                    "baseUrl": "./",
                    "incremental": true,
                    "skipLibCheck": true,
                    "strictNullChecks": false,
                    "noImplicitAny": false,
                    "strictBindCallApply": false,
                    "forceConsistentCasingInFileNames": false,
                    "noFallthroughCasesInSwitch": false
                  }
                }
                """;
    }

    private String envExample(String projectName) {
        return """
                PORT=3000
                DB_HOST=localhost
                DB_PORT=5432
                DB_USERNAME=postgres
                DB_PASSWORD=password
                DB_DATABASE=%s
                JWT_SECRET=your-secret-key
                """.formatted(projectName);
    }

    private String nestGitignore() {
        return """
                node_modules/
                dist/
                .env
                .DS_Store
                *.js.map
                """;
    }

    private String readme(String projectName, String license) {
        return """
                # %s
                                
                ## Getting Started
                
                ### Prerequisites
                - Node.js 20+
                
                ### Run
```bash
                npm install
                npm run start:dev
```
                
                ## License
                %s
                """.formatted(projectName, license);
    }
}