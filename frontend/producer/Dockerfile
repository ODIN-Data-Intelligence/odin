FROM node:20-alpine AS builder
WORKDIR /app

# Install dependencies with workspace awareness
COPY package.json ./
COPY shared/package.json shared/
COPY producer/package.json producer/
RUN npm install --workspace=shared --workspace=producer

# Copy source and build
COPY shared shared
COPY producer producer
RUN npm run build:producer

FROM nginxinc/nginx-unprivileged:alpine
# gettext provides envsubst for runtime config injection
USER root
RUN apk add --no-cache gettext
USER nginx

COPY --from=builder /app/producer/dist /usr/share/nginx/html
COPY producer/nginx.conf /etc/nginx/conf.d/default.conf
COPY producer/docker-entrypoint.sh /docker-entrypoint.sh
USER root
RUN chmod +x /docker-entrypoint.sh \
 && chown -R nginx:nginx /usr/share/nginx/html
USER nginx

EXPOSE 8080
ENTRYPOINT ["/docker-entrypoint.sh"]
