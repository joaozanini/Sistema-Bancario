# BANTADS — Internet Banking do TADS

Sistema de Internet Banking desenvolvido para a disciplina DS152 — Desenvolvimento de Aplicações Corporativas (UFPR, TADS, 2026/2) - DAC


## Arquitetura

- **Front-end:** Angular
- **API Gateway:** Node.js — ponto único de acesso, API Composition, cache
- **Microsserviços:** Spring Boot (Java) — MS Conta (CQRS + Event Sourcing), MS Cliente, MS Gerente, MS Auth, Orquestrador SAGA, MS Email
- **Bancos de dados:** PostgreSQL (transacional + event store), MongoDB (autenticação), Redis (cache, tokens, jobs, estado das SAGAs)
- **Mensageria:** RabbitMQ
- **Padrões:** API Gateway, Database per Service, CQRS, Event Sourcing, SAGA Orquestrada, API Composition
- **Infra:** Docker / docker-compose

## Equipe

| Dupla | Integrantes |
|---|---|
| CT — Contas & Domínio | João Vitor Zanini Pedro, Lucas Sarnacki Guiraud |
| PL — Plataforma & SAGA | Gabriel Silva Costa, Danniel Eduardo Dorox |
| FE — Frontend | Reinaldo Antonio Castellano Neto, Murilo da Silva Santos |

## Configuração

Copie `.env.example` para `.env` e preencha as variáveis locais (senha de app do Gmail, chave secreta do JWT etc.). **Nunca** commitar o `.env` com os valores reais.

Cada microsserviço Spring Boot lê `src/main/resources/application.properties`, que não é versionado: o repositório traz só o `application.properties.example`, sem segredos (os valores vêm das variáveis do `.env`). Depois de clonar, copie o exemplo em cada serviço:

```bash
for s in services/ms-*; do cp -n "$s/src/main/resources/application.properties.example" "$s/src/main/resources/application.properties"; done
```

Sem essa cópia o serviço sobe sem porta, schema do banco e política de retentativa, e os testes do Orquestrador falham. O PostgreSQL usa um schema por serviço (`conta`, `cliente`, `gerente`), criado pelo Flyway na primeira subida.

## Como executar

Só é preciso ter o Docker com o Compose (`docker compose`). JDK, Maven e Node não são necessários, porque cada Dockerfile é multi-stage e compila o próprio serviço. O build das imagens usa sempre o `application.properties.example` de cada serviço, então a cópia acima só é necessária para rodar um serviço fora do Docker.

```bash
cp .env.example .env   # preencha POSTGRES_PASSWORD, REDIS_PASSWORD, RABBITMQ_PASSWORD e JWT_SECRET
./start.sh             # no Windows, rode pelo WSL ou pelo Git Bash
```

O `start.sh` faz o build das imagens, sobe os contêineres e espera todos os healthchecks passarem.

| O quê | Onde |
|---|---|
| Front-end | http://localhost:4200 |
| API Gateway | http://localhost:3000 |
| Console do RabbitMQ | http://localhost:15672 |
| PostgreSQL, MongoDB e Redis (DBeaver etc.) | localhost:5432, localhost:27017 e localhost:6379 |

Os microsserviços não publicam portas: só o API Gateway fala com eles, pela rede interna do compose.

Para parar, use `docker compose down`. Para apagar também os dados dos volumes, use `docker compose down -v`.

## CI/CD

O pipeline fica em `.github/workflows/ci-cd.yml` e roda no GitHub Actions:

- **CI** (todo push e pull request para a `main`): `mvnw verify` de cada microsserviço Java contra Postgres e Mongo reais, `npm test` do API Gateway, e lint, testes e build do frontend. Também constrói todas as imagens Docker.
- **CD** (só em push na `main`, com o CI verde): publica as imagens em `ghcr.io/<dono-do-repo>/bantads-<serviço>`, com a tag do commit e `latest`.
