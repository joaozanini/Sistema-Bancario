#!/usr/bin/env bash
# Sobe a frota inteira do BANTADS: prepara config local, gera os jars, constroi
# as imagens e levanta o docker-compose. Idempotente — rodar de novo nao estraga
# nada e so refaz o que mudou.
set -euo pipefail

cd "$(dirname "$0")"

SERVICOS_SPRING=(ms-auth ms-cliente ms-conta ms-gerente ms-orquestrador ms-email)
PULAR_BUILD="${PULAR_BUILD:-0}"

info() { printf '\n\033[1;34m==>\033[0m %s\n' "$1"; }
erro() { printf '\n\033[1;31mERRO:\033[0m %s\n' "$1" >&2; exit 1; }

# --- 1. pre-requisitos ------------------------------------------------------
command -v docker >/dev/null || erro "docker nao encontrado no PATH."
docker compose version >/dev/null 2>&1 || erro "'docker compose' indisponivel. Suba o Docker Desktop."

# --- 2. memoria disponivel --------------------------------------------------
# A soma dos mem_limit do compose precisa caber na maquina com folga para SO,
# IDE, navegador e console do RabbitMQ, que ficam abertos na defesa.
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
# Nao sao versionados (so o .example). Precisam existir ANTES do package: o
# Maven empacota src/main/resources dentro do jar.
info "Preparando application.properties"
for servico in "${SERVICOS_SPRING[@]}"; do
  destino="services/$servico/src/main/resources/application.properties"
  if [ ! -f "$destino" ]; then
    cp "$destino.example" "$destino"
    echo "  criado  $servico"
  else
    echo "  ja existe  $servico"
  fi
done

# --- 5. jars ----------------------------------------------------------------
# Os Dockerfiles fazem COPY target/*.jar: sem o package antes, o build da imagem falha.
if [ "$PULAR_BUILD" = "1" ]; then
  info "PULAR_BUILD=1, reaproveitando os jars existentes"
else
  info "Gerando os jars (pode demorar na primeira vez)"
  for servico in "${SERVICOS_SPRING[@]}"; do
    echo "  empacotando $servico"
    (cd "services/$servico" && ./mvnw -B -ntp -q clean package -DskipTests)
  done
fi

for servico in "${SERVICOS_SPRING[@]}"; do
  compgen -G "services/$servico/target/*.jar" >/dev/null || erro "jar nao gerado para $servico"
done

# --- 6. imagens e containers ------------------------------------------------
info "Construindo as imagens"
docker compose build

info "Subindo a frota"
docker compose up -d

info "Aguardando os healthchecks"
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
