FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
# copia a pasta do index.html gerado para /app/publico
RUN npm run build \
 && mkdir -p /app/publico \
 && cp -r "$(dirname "$(find dist -name index.html | head -1)")"/. /app/publico/

FROM nginx:1.27-alpine
COPY --from=build /app/publico /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
