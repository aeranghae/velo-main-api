package cloud.velo.main.util.template.initializer.framework;

import cloud.velo.main.controller.dto.ProjectCreateRequestDto;
import cloud.velo.main.util.template.BaseTemplateInitializer;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

@Component
public class ReactTemplateInitializer extends BaseTemplateInitializer {

    @Override
    public String getSupportedFramework() { return "react"; }

    @Override
    public void initTemplate(Path rootPath, ProjectCreateRequestDto dto) {
        String projectName = dto.getArtifact().toLowerCase();

        createDirectories(rootPath, List.of(
                "src/assets",
                "src/components/common",
                "src/hooks",
                "src/pages",
                "src/router",
                "src/services",
                "src/stores",
                "src/types",
                "public"
        ));

        //writeFile(rootPath.resolve("src/main.tsx"), mainTsx());
        //writeFile(rootPath.resolve("src/App.tsx"), appTsx());
        //writeFile(rootPath.resolve("src/router/index.tsx"), routerIndex());
        //writeFile(rootPath.resolve("src/services/api.ts"), apiTs());
        //writeFile(rootPath.resolve("src/types/index.ts"), typesIndex());
        //writeFile(rootPath.resolve("index.html"), indexHtml(projectName));
        //writeFile(rootPath.resolve("package.json"), packageJson(projectName));
        //writeFile(rootPath.resolve("vite.config.ts"), viteConfig());
        //writeFile(rootPath.resolve("tsconfig.json"), tsconfig());
        //writeFile(rootPath.resolve(".env.example"), envExample());
        //writeFile(rootPath.resolve(".gitignore"), reactGitignore());
        //writeFile(rootPath.resolve("README.md"), readme(projectName, dto.getLicense()));
    }

    private String mainTsx() {
        return """
                import React from 'react'
                import ReactDOM from 'react-dom/client'
                import { RouterProvider } from 'react-router-dom'
                import router from './router'
                import './index.css'
                
                ReactDOM.createRoot(document.getElementById('root')!).render(
                  <React.StrictMode>
                    <RouterProvider router={router} />
                  </React.StrictMode>
                )
                """;
    }

    private String appTsx() {
        return """
                import { Outlet } from 'react-router-dom'
                
                function App() {
                  return (
                    <div>
                      <Outlet />
                    </div>
                  )
                }
                
                export default App
                """;
    }

    private String routerIndex() {
        return """
                import { createBrowserRouter } from 'react-router-dom'
                import App from '../App'
                import HomePage from '../pages/HomePage'
                
                const router = createBrowserRouter([
                  {
                    path: '/',
                    element: <App />,
                    children: [
                      {
                        index: true,
                        element: <HomePage />
                      }
                    ]
                  }
                ])
                
                export default router
                """;
    }

    private String apiTs() {
        return """
                import axios from 'axios'
                
                const api = axios.create({
                  baseURL: import.meta.env.VITE_API_BASE_URL,
                  timeout: 10000,
                })
                
                api.interceptors.request.use((config) => {
                  const token = localStorage.getItem('token')
                  if (token) config.headers.Authorization = `Bearer ${token}`
                  return config
                })
                
                api.interceptors.response.use(
                  (response) => response,
                  (error) => {
                    if (error.response?.status === 401) {
                      localStorage.removeItem('token')
                      window.location.href = '/login'
                    }
                    return Promise.reject(error)
                  }
                )
                
                export default api
                """;
    }

    private String typesIndex() {
        return """
                // 공통 타입 정의
                export interface ApiResponse<T> {
                  data: T
                  message: string
                  status: number
                }
                
                export interface PageResponse<T> {
                  content: T[]
                  totalElements: number
                  totalPages: number
                  size: number
                  number: number
                }
                """;
    }

    private String indexHtml(String projectName) {
        return """
                <!DOCTYPE html>
                <html lang="ko">
                  <head>
                    <meta charset="UTF-8" />
                    <link rel="icon" type="image/svg+xml" href="/vite.svg" />
                    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                    <title>%s</title>
                  </head>
                  <body>
                    <div id="root"></div>
                    <script type="module" src="/src/main.tsx"></script>
                  </body>
                </html>
                """.formatted(projectName);
    }

    private String packageJson(String projectName) {
        return """
                {
                  "name": "%s",
                  "version": "0.0.0",
                  "type": "module",
                  "scripts": {
                    "dev": "vite",
                    "build": "tsc && vite build",
                    "preview": "vite preview"
                  },
                  "dependencies": {
                    "react": "^18.3.0",
                    "react-dom": "^18.3.0",
                    "react-router-dom": "^6.26.0",
                    "axios": "^1.7.0",
                    "zustand": "^4.5.0"
                  },
                  "devDependencies": {
                    "@types/react": "^18.3.0",
                    "@types/react-dom": "^18.3.0",
                    "@vitejs/plugin-react": "^4.3.0",
                    "typescript": "^5.5.0",
                    "vite": "^5.4.0"
                  }
                }
                """.formatted(projectName);
    }

    private String viteConfig() {
        return """
                import { defineConfig } from 'vite'
                import react from '@vitejs/plugin-react'
                import { fileURLToPath, URL } from 'node:url'
                
                export default defineConfig({
                  plugins: [react()],
                  resolve: {
                    alias: {
                      '@': fileURLToPath(new URL('./src', import.meta.url))
                    }
                  },
                  server: {
                    port: 3000,
                    proxy: {
                      '/api': {
                        target: 'http://localhost:8080',
                        changeOrigin: true
                      }
                    }
                  }
                })
                """;
    }

    private String tsconfig() {
        return """
                {
                  "compilerOptions": {
                    "target": "ES2020",
                    "useDefineForClassFields": true,
                    "lib": ["ES2020", "DOM", "DOM.Iterable"],
                    "module": "ESNext",
                    "skipLibCheck": true,
                    "moduleResolution": "bundler",
                    "allowImportingTsExtensions": true,
                    "resolveJsonModule": true,
                    "isolatedModules": true,
                    "noEmit": true,
                    "jsx": "react-jsx",
                    "strict": true,
                    "paths": {
                      "@/*": ["./src/*"]
                    }
                  },
                  "include": ["src"],
                  "exclude": ["node_modules"]
                }
                """;
    }

    private String envExample() {
        return "VITE_API_BASE_URL=http://localhost:8080/api/v1\n";
    }

    private String reactGitignore() {
        return """
                node_modules/
                dist/
                .env
                .env.local
                .DS_Store
                *.local
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
                npm run dev
```
                
                ## License
                %s
                """.formatted(projectName, license);
    }
}