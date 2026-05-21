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

FROM nginx:alpine
COPY --from=builder /app/consumer/dist /usr/share/nginx/html
COPY consumer/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
