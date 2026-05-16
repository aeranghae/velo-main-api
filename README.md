
---

# Velo 설정

## 1. 데이터베이스 (PostgreSQL, Redis - Docker)

```shell
docker run --name velo-main-db \
  -e POSTGRES_USER=aeranghae \
  -e POSTGRES_PASSWORD='원하는비밀번호' \
  -e POSTGRES_DB=velo_main_db \
  -p 5432:5432 \
  -v ~/db_data:/var/lib/postgresql/data \ # 데이터 저장 경로
  -d postgres:15
```

```shell
docker run --name velo-main-redis \
  -p 6379:6379 \
  -v /home/{경로}/redis-data:/data \ # 데이터 저장 경로
  --restart always \
  -d redis redis-server --appendonly yes --requirepass '원하는비밀번호'
```
## 2. 쿠버네티스

- k8s가 설치되어 있는 기준 (k3s는 사용안해봐서 모름)
- 쿠버네티스 사용 시 NFS 설정하는것을 권장합니다 (PV, PVC설정 후 사용)

설정파일은 다음과 같이 적용하여 쿠버네티스 Deployment 설정에서 주입해주면 됩니다.
```bash
# application.yml application-oauth.yml 파일의 경로를 사용해 Secret 생성
kubectl create secret generic velo-config \
  --from-file=application.yml=application.yml \
  --from-file=application-oauth.yml=application-oauth.yml
```

## 3. 그 외 사용된 기능
- Nginx + Certbot

## License
이 프로젝트는 Apache License 2.0을 따릅니다. 자세한 내용은 [LICENSE](./LICENSE) 파일을 확인하세요.