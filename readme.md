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
