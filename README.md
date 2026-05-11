
---

# 애랑해(aeranghae) 개발환경 설정

## 1. 데이터베이스 (PostgreSQL - Docker)

```shell
docker run --name aeranghae-db \
  -e POSTGRES_USER=aeranghae \
  -e POSTGRES_PASSWORD='' \
  -e POSTGRES_DB=aeranghae_db \
  -p 5432:5432 \
  -v ~/db_data:/var/lib/postgresql/data \
  -d postgres:15
```

## 2. Nginx + Certbot

### 1. 기본 환경 준비 및 Nginx 설치

먼저 웹 서버인 Nginx를 설치하고, 외부 접속을 위해 방화벽에서 HTTP(80)와 HTTPS(443) 포트를 개방합니다.

```Bash
# Nginx 설치 및 자동 실행 등록
sudo dnf install -y nginx
sudo systemctl enable --now nginx

# 방화벽 개방 (HTTP, HTTPS)
sudo firewall-cmd --permanent --add-service=http
sudo firewall-cmd --permanent --add-service=https
sudo firewall-cmd --reload
```

### 2. Certbot 설치 및 SSL 인증서 발급

무료 SSL 인증서인**Let's Encrypt**를 사용하여`oxxultus.cloud`도메인에 대한 인증서를 발급받습니다.

```Bash
# Certbot 및 Nginx 플러그인 설치
sudo dnf install -y certbot python3-certbot-nginx

# 인증서 발급 (자동으로 Nginx 설정 반영)
sudo certbot --nginx -d oxxultus.cloud
```

- **주의:**실행 시 입력하는 이메일은 인증서 만료 안내를 위해 실제 사용 중인 메일을 권장합니다.
- **갱신 확인:**`sudo certbot renew --dry-run --nginx`명령어로 자동 갱신이 정상 작동하는지 테스트할 수 있습니다.

### 3. Nginx 리버스 프록시 설정

사용자가`oxxultus.cloud`로 접속하면 Nginx가 이를 받아 내부에서 실행 중인 스프링 부트(8080 포트)로 전달하도록 설정합니다.

설정 파일 위치:`/etc/nginx/conf.d/oxxultus.conf`(신규 생성 권장)

```Nginx
server {
    listen       80;
    listen       [::]:80;
    server_name  oxxultus.cloud;

    # 모든 HTTP 요청을 HTTPS로 강제 리다이렉트 (보안 강화)
    return 301 https://$host$request_uri;
}

server {
    listen       443 ssl http2;
    listen       [::]:443 ssl;
    http2 on;# http2를 별도 라인으로 분리
    server_name  oxxultus.cloud;

    # SSL 인증서 경로 (Certbot이 자동 삽입하지만 위치 확인 필요)
    ssl_certificate /etc/letsencrypt/live/oxxultus.cloud/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/oxxultus.cloud/privkey.pem;

    # 프록시 설정: 모든 요청을 스프링 부트로 전달
    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        # 타임아웃 설정 (LLM 응답 대기 시간 고려)
        proxy_read_timeout 300;
        proxy_connect_timeout 300;
        proxy_send_timeout 300;
    }

    # 기본 에러 페이지
    error_page 404 /404.html;
    location = /404.html { }

    error_page 500 502 503 504 /50x.html;
    location = /50x.html { }
}
```

### 4. 설정 반영 및 검증

수정한 설정 파일에 문법적 오류가 없는지 확인한 후 Nginx를 재로드합니다.

```Bash
# Nginx 설정 파일 문법 검사
sudo nginx -t

# 문법이 OK라면 서비스 재로드 (기존 연결 유지하며 반영)
sudo systemctl reload nginx
```

### 🛠️ 설정 파일 수정 (권장)

`sudo vi /etc/letsencrypt/renewal/oxxultus.cloud.conf` 명령어로 파일을 열어 `authenticator` 부분을 아래와 같이 수정하세요.

```Ini, TOML
# 수정 전
authenticator = standalone

# 수정 후
authenticator = nginx
installer = nginx
```

- **`authenticator = nginx`**: 인증을 위해 Nginx 플러그인을 사용합니다.
- **`installer = nginx`**: 갱신된 인증서를 Nginx 설정에 자동으로 적용합니다.