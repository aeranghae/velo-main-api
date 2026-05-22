package cloud.velo.main.util.template.initializer.framework;

import cloud.velo.main.controller.dto.ProjectCreateRequestDto;
import cloud.velo.main.util.template.BaseTemplateInitializer;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

@Component
public class VueTemplateInitializer extends BaseTemplateInitializer {

    @Override
    public String getSupportedFramework() { return "vue"; }

    @Override
    public void initTemplate(Path rootPath, ProjectCreateRequestDto dto) {
        String projectName = dto.getArtifact().toLowerCase();

        createDirectories(rootPath, List.of(
                "src/assets",
                "src/components/common",
                "src/composables",
                "src/router",
                "src/stores",
                "src/views",
                "src/types",
                "src/api",
                "public"
        ));

        //writeFile(rootPath.resolve("src/main.ts"), mainTs());
        //writeFile(rootPath.resolve("src/App.vue"), appVue());
        //writeFile(rootPath.resolve("src/router/index.ts"), routerIndex());
        //writeFile(rootPath.resolve("src/stores/counter.ts"), sampleStore());
        //writeFile(rootPath.resolve("src/api/index.ts"), apiIndex());
        //writeFile(rootPath.resolve("package.json"), packageJson(projectName));
        //writeFile(rootPath.resolve("vite.config.ts"), viteConfig());
        //writeFile(rootPath.resolve("tsconfig.json"), tsconfig());
        //writeFile(rootPath.resolve(".env.example"), envExample());
        //writeFile(rootPath.resolve(".gitignore"), vueGitignore());
        //writeFile(rootPath.resolve("README.md"), readme(projectName, dto.getLicense()));
    }

    private String mainTs() {
        return """
                import { createApp } from 'vue'
                import { createPinia } from 'pinia'
                import App from './App.vue'
                import router from './router'
                import './assets/main.css'
                
                const app = createApp(App)
                
                app.use(createPinia())
                app.use(router)
                app.mount('#app')
                """;
    }

    private String appVue() {
        return """
                <script setup lang="ts">
                import { RouterView } from 'vue-router'
                </script>
                
                <template>
                  <RouterView />
                </template>
                """;
    }

    private String routerIndex() {
        return """
                import { createRouter, createWebHistory } from 'vue-router'
                import HomeView from '../views/HomeView.vue'
                
                const router = createRouter({
                  history: createWebHistory(import.meta.env.BASE_URL),
                  routes: [
                    {
                      path: '/',
                      name: 'home',
                      component: HomeView
                    }
                  ]
                })
                
                export default router
                """;
    }

    private String sampleStore() {
        return """
                import { defineStore } from 'pinia'
                import { ref } from 'vue'
                
                export const useCounterStore = defineStore('counter', () => {
                  const count = ref(0)
                  const increment = () => count.value++
                  return { count, increment }
                })
                """;
    }

    private String apiIndex() {
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
                
                export default api
                """;
    }

    private String packageJson(String projectName) {
        return """
                {
                  "name": "%s",
                  "version": "0.0.0",
                  "scripts": {
                    "dev": "vite",
                    "build": "vue-tsc --noEmit && vite build",
                    "preview": "vite preview"
                  },
                  "dependencies": {
                    "vue": "^3.5.0",
                    "vue-router": "^4.4.0",
                    "pinia": "^2.2.0",
                    "axios": "^1.7.0"
                  },
                  "devDependencies": {
                    "@vitejs/plugin-vue": "^5.0.0",
                    "typescript": "^5.5.0",
                    "vite": "^6.0.0",
                    "vue-tsc": "^2.0.0"
                  }
                }
                """.formatted(projectName);
    }

    private String viteConfig() {
        return """
                import { defineConfig } from 'vite'
                import vue from '@vitejs/plugin-vue'
                import { fileURLToPath, URL } from 'node:url'
                
                export default defineConfig({
                  plugins: [vue()],
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
                    "module": "ESNext",
                    "moduleResolution": "bundler",
                    "strict": true,
                    "jsx": "preserve",
                    "skipLibCheck": true,
                    "paths": {
                      "@/*": ["./src/*"]
                    }
                  },
                  "include": ["src/**/*"],
                  "exclude": ["node_modules"]
                }
                """;
    }

    private String envExample() {
        return "VITE_API_BASE_URL=http://localhost:8080/api/v1\n";
    }

    private String vueGitignore() {
        return """
                node_modules/
                dist/
                .env
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