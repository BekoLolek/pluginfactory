.PHONY: dev test build deploy-staging deploy-prod clean

dev:
	docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d

test:
	cd api && ./mvnw clean verify
	cd web && npm run lint && npm run build

build:
	docker compose build

deploy-staging:
	./infra/scripts/deploy.sh $(STAGING_HOST) develop

deploy-prod:
	./infra/scripts/deploy.sh $(PRODUCTION_HOST) main

clean:
	cd api && ./mvnw clean
	cd web && rm -rf dist node_modules/.cache
	docker compose down -v
