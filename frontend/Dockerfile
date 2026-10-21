# Etapa 1: gera o build de produção do Angular
FROM node:24-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npx ng build

# Etapa 2: o nginx serve só os arquivos estáticos gerados
FROM nginx:1.30-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/bantads-frontend/browser /usr/share/nginx/html
EXPOSE 80
