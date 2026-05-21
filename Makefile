.PHONY: up up-ai down build build-backend build-frontend \
        dev-producer dev-consumer test \
        migrate reindex seed-vocab seed logs pull clean wrapper help

## Start core infrastructure + application services
up:
	docker compose up -d

## Start with AI profile (includes Ollama)
up-ai:
	docker compose --profile ai up -d

## Stop all containers
down:
	docker compose down

## Build everything
build: build-backend build-frontend

## Build all Java services
build-backend:
	./gradlew build -x test

## Build both frontend apps
build-frontend:
	cd frontend && npm install && npm run build

## Start producer dev server (port 3000)
dev-producer:
	cd frontend && npm run dev:producer

## Start consumer dev server (port 3001)
dev-consumer:
	cd frontend && npm run dev:consumer

## Run all Java tests
test:
	./gradlew test

## Run tests for a specific service  e.g. make test-svc svc=catalog-service
test-svc:
	./gradlew :services:$(svc):test

## Run Flyway migrations for all services (requires running DBs)
migrate:
	./gradlew :services:catalog-service:flywayMigrate \
	          :services:harvest-service:flywayMigrate \
	          :services:lineage-service:flywayMigrate \
	          :services:identity-service:flywayMigrate \
	          :services:ai-service:flywayMigrate

## Trigger full re-index of OpenSearch from catalog-service
reindex:
	curl -s -X POST http://localhost:8004/api/v1/admin/reindex | jq .

## Seed system vocabularies (schema.org + FIBO) via catalog-service
seed-vocab:
	curl -s -X POST http://localhost:8001/api/v1/admin/seed/vocabularies | jq .

## Load rich sample data (Meridian Capital investment bank scenario)
seed:
	bash infra/seed/seed.sh

## Follow logs for a specific service  e.g. make logs svc=catalog-service
logs:
	docker compose logs -f $(svc)

## Pull all Docker images without starting
pull:
	docker compose pull

## Remove build artifacts and local docker volumes
clean:
	./gradlew clean
	docker compose down -v

## Install Gradle wrapper (run once if wrapper is missing)
wrapper:
	java -jar gradle/wrapper/gradle-wrapper.jar --gradle-version 8.8

help:
	@grep -E '^##' Makefile | sed 's/## //'
