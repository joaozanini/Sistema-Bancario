FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
# O caminho de saida do Angular varia com a versao do builder; em vez de fixar
# dist/<projeto>/browser, localiza o index.html gerado e normaliza em /app/publico.
RUN npm run build \
 && mkdir -p /app/publico \
 && cp -r "$(dirname "$(find dist -name index.html | head -1)")"/. /app/publico/

FROM nginx:1.27-alpine
COPY --from=build /app/publico /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
