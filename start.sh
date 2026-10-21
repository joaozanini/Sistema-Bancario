#!/usr/bin/env bash
# Constrói as imagens de todos os serviços e sobe o BANTADS com o docker compose (enunciado, seção 5.12).
set -euo pipefail

cd "$(dirname "$0")"

if [ ! -f .env ]; then
  echo "Arquivo .env não encontrado. Crie a partir do exemplo e preencha as senhas:" >&2
  echo "  cp .env.example .env" >&2
  exit 1
fi

echo "==> Build das imagens (front-end, API Gateway e microsserviços)"
echo "    Na primeira vez demora alguns minutos: baixa as imagens base e as dependências do Maven e do npm."
docker compose build

echo "==> Subindo os contêineres e aguardando os healthchecks"
if ! docker compose up -d --wait; then
  echo "Algum contêiner não ficou saudável. Veja o estado com 'docker compose ps' e os logs com 'docker compose logs <serviço>'." >&2
  exit 1
fi

echo
echo "BANTADS no ar:"
echo "  Front-end ............ http://localhost:4200"
echo "  API Gateway .......... http://localhost:3000"
echo "  Console do RabbitMQ .. http://localhost:15672"
