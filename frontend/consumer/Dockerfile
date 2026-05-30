FROM node:20-alpine AS builder
WORKDIR /app

# Install dependencies with workspace awareness
COPY package.json ./
COPY shared/package.json shared/
COPY consumer/package.json consumer/
RUN npm install --workspace=shared --workspace=consumer

# Copy source and build
COPY shared shared
COPY consumer consumer
RUN npm run build:consumer

FROM nginxinc/nginx-unprivileged:alpine
USER root
RUN apk add --no-cache gettext

COPY --from=builder /app/consumer/dist /usr/share/nginx/html
COPY consumer/nginx.conf.tmpl /etc/nginx/conf.d/default.conf.tmpl
COPY consumer/docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh \
 && chown -R nginx:nginx /usr/share/nginx/html
USER nginx

EXPOSE 8080
ENTRYPOINT ["/docker-entrypoint.sh"]
