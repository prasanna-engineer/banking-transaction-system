# Deploying the demo

This guide puts the application online as a **portfolio demo with fictional data**. It is not a guide to
running a real bank: that needs professional security review, monitoring, backups, compliance and more.

## Before you start: production checklist

| Item | Why | How |
|---|---|---|
| HTTPS everywhere | Tokens and passwords must never travel in plain text | Use the host's managed TLS or Caddy (below) |
| New secrets | Never reuse local values | Fresh `JWT_SECRET` (≥ 32 chars), DB passwords, generated on the server |
| Secrets outside Git | The repo may be public | Host's secret manager or a server-only `.env` file (`chmod 600`) |
| Exact CORS origin | Only your frontend may call the API from a browser | `CORS_ALLOWED_ORIGINS=https://your-frontend-domain` |
| Swagger off (optional) | Less surface for a public demo | `API_DOCS_ENABLED=false` |
| Demo data on purpose | `db/seed/R__demo_data.sql` runs wherever it is on Flyway's path | Keep it for a demo; for real users set `SPRING_FLYWAY_LOCATIONS=classpath:db/migration` |
| Database not public | Only the backend should reach it | No public port / security group open to `0.0.0.0/0` |
| Backups | Data loss | Enable the host's automated backups or schedule `mariadb-dump` |
| Supported database | MariaDB 10.4 is end-of-life | Use MariaDB 11.4 LTS (as in `docker-compose.yml`) or MySQL 8.4 |

## Option A — one small Linux server with Docker Compose (simplest, closest to local)

Works on any VPS (e.g. AWS Lightsail or EC2, DigitalOcean, Hetzner). 2 GB RAM is a comfortable minimum.

1. **Create the server** (Ubuntu LTS) and point two DNS records at its IP, e.g. `bank.example.com`
   (frontend) and `api.bank.example.com` (backend). Allow only ports 22, 80 and 443 in the firewall.
2. **Install Docker** following the official instructions for Ubuntu (docs.docker.com/engine/install/ubuntu).
3. **Get the code and create secrets on the server**:
   ```bash
   git clone https://github.com/stacwiz-prasanna/banking-transaction-system.git
   cd banking-transaction-system
   cp .env.example .env && chmod 600 .env
   nano .env   # set SPRING_DATASOURCE_PASSWORD, DB_ROOT_PASSWORD, JWT_SECRET (openssl rand -base64 48)
   ```
4. **Point the frontend and CORS at the real domains** in `docker-compose.yml`:
   - `backend.environment.CORS_ALLOWED_ORIGINS: https://bank.example.com`
   - `frontend.build.args.VITE_API_BASE_URL: https://api.bank.example.com/api/v1`
   - remove the `ports:` sections of `backend` and `frontend` (the proxy below reaches them inside Docker).
5. **Add HTTPS with Caddy** (automatic Let's Encrypt certificates). Add to `docker-compose.yml`:
   ```yaml
     caddy:
       image: caddy:2
       ports: ["80:80", "443:443"]
       volumes:
         - ./Caddyfile:/etc/caddy/Caddyfile:ro
         - caddy-data:/data
       depends_on: [frontend, backend]
   ```
   add `caddy-data:` under `volumes:`, and create `Caddyfile`:
   ```
   bank.example.com {
       reverse_proxy frontend:80
   }
   api.bank.example.com {
       reverse_proxy backend:8081
   }
   ```
6. **Start it**: `docker compose up -d --build`, then check `docker compose ps` and
   `docker compose logs -f backend` until you see `Started BankingApplication`.
7. **Verify**: `https://api.bank.example.com/api/v1/health` returns `UP`, and the login works at
   `https://bank.example.com`.

Updating later: `git pull && docker compose up -d --build`. Flyway applies any new migrations on start-up.

## Option B — managed platforms (no server to maintain)

Any platform that runs a Dockerfile and offers a managed MySQL/MariaDB works (e.g. AWS App Runner + RDS,
Render, Railway, Fly.io). The pieces map like this:

| Piece | Deploy as | Settings |
|---|---|---|
| Database | Managed MariaDB 11 / MySQL 8 | Create database `banking_mgmt_db` and user `banking_app` |
| Backend | Web service from `backend/Dockerfile`, port 8081 | Env vars: `SPRING_DATASOURCE_URL` (`jdbc:mariadb://<host>:3306/banking_mgmt_db`), `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS`, optionally `API_DOCS_ENABLED=false` |
| Frontend | Static site: build `npm ci && npm run build` in `frontend/`, publish `dist/` | Build variable `VITE_API_BASE_URL=https://<backend-url>/api/v1`; add a rewrite of all paths to `/index.html` (single-page app) |

Check the platform's current pricing and free-tier limits before creating resources, and set a
billing alert.

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| Backend exits immediately with `JWT_SECRET must be at least 32 bytes` | Secret missing or too short |
| `Connection refused` / `Unknown database` | Wrong `SPRING_DATASOURCE_URL`, database not ready, or firewall |
| Browser shows a CORS error | `CORS_ALLOWED_ORIGINS` does not exactly match the frontend URL (scheme + host + port) |
| Refreshing `/accounts/1` gives 404 | The static host is missing the "all paths → index.html" rewrite |
| Login works locally but not online | Frontend was built with the wrong `VITE_API_BASE_URL` (it is fixed at build time) |
