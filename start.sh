#!/usr/bin/env bash
# Sobe a frota do BANTADS: prepara a config, constrói as imagens e sobe o compose
set -euo pipefail

cd "$(dirname "$0")"

SERVICOS_SPRING=(ms-auth ms-cliente ms-conta ms-gerente ms-orquestrador ms-email)

info() { printf '\n\033[1;34m==>\033[0m %s\n' "$1"; }
erro() { printf '\n\033[1;31mERRO:\033[0m %s\n' "$1" >&2; exit 1; }

# --- 1. pre-requisitos ------------------------------------------------------
command -v docker >/dev/null || erro "docker nao encontrado no PATH."
docker compose version >/dev/null 2>&1 || erro "'docker compose' indisponivel. Suba o Docker Desktop."

# --- 2. memoria disponivel --------------------------------------------------
exigido_mb=$(grep -oE 'mem_limit: [0-9]+m' docker-compose.yml | grep -oE '[0-9]+' | awk '{s+=$1} END {print s}')
total_mb=$(docker info --format '{{.MemTotal}}' 2>/dev/null | awk '{printf "%d", $1/1048576}') || total_mb=0

if [ "${total_mb:-0}" -gt 0 ]; then
  echo "Memoria: a frota reserva ${exigido_mb} MB; o Docker ve ${total_mb} MB."
  if [ "$total_mb" -lt "$exigido_mb" ]; then
    erro "o Docker so tem ${total_mb} MB e a frota precisa de ${exigido_mb} MB.
       Aumente a memoria do Docker Desktop (Settings > Resources) ou suba
       menos servicos: docker compose up -d postgres mongo redis rabbitmq"
  fi
  folga_mb=$((total_mb - exigido_mb))
  [ "$folga_mb" -ge 2048 ] || echo "  AVISO: sobram so ${folga_mb} MB para o resto do sistema (recomendado 2048+)."
fi

# --- 3. .env ----------------------------------------------------------------
if [ ! -f .env ]; then
  cp .env.example .env
  erro ".env nao existia e foi criado a partir do .env.example.
       Preencha os valores (inclusive GMAIL_USERNAME e GMAIL_APP_PASSWORD) e rode de novo."
fi

faltando=()
for chave in POSTGRES_USER POSTGRES_PASSWORD POSTGRES_DB REDIS_PASSWORD RABBITMQ_USER RABBITMQ_PASSWORD JWT_SECRET; do
  grep -qE "^${chave}=.+" .env || faltando+=("$chave")
done
[ ${#faltando[@]} -eq 0 ] || erro "variaveis sem valor no .env: ${faltando[*]}"

# --- 4. application.properties de cada MS -----------------------------------
info "Preparando application.properties"
for servico in "${SERVICOS_SPRING[@]}"; do
  destino="services/$servico/src/main/resources/application.properties"
  if [ ! -f "$destino" ]; then
    cp "$destino.example" "$destino"
    echo "  criado  $servico"
  elif [ "$destino.example" -nt "$destino" ]; then
    cp "$destino.example" "$destino"
    echo "  atualizado  $servico (o .example mudou; valores reais vao no .env)"
  else
    echo "  ja existe  $servico"
  fi
done

# --- 5. imagens e containers ------------------------------------------------
info "Construindo as imagens (demora na primeira vez)"
docker compose build

# Sobe os containers e espera os healthchecks
info "Subindo a frota e aguardando os healthchecks"
docker compose up -d --wait
docker compose ps

cat <<'FIM'

Frota no ar. Pontos de acesso:
  Front .................. http://localhost:4200
  API Gateway ............ http://localhost:3000
  RabbitMQ (console) ..... http://localhost:15672

  docker compose logs -f <servico>   acompanha um servico
  docker compose down                derruba tudo
  docker compose down -v             derruba e apaga os volumes (zera os bancos)
FIM
