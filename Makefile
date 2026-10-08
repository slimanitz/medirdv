.PHONY: up up-obs down logs test test-backend test-web build-web lint seed demo token

JAVA_ENV = JAVA_HOME=$${JAVA_HOME:-$$HOME/.local/opt/jdk21}

up: ## Build and start the whole product
	docker compose up -d --build

up-obs: ## Same plus Prometheus, Tempo and Grafana, with trace export
	TRACING_EXPORT_ENABLED=true docker compose --profile observability up -d --build

down: ## Stop everything and delete volumes
	docker compose --profile observability down -v

logs:
	docker compose logs -f --tail=100

test: test-backend test-web ## All tests

test-backend: ## Unit + Testcontainers integration tests (needs Docker)
	$(JAVA_ENV) mvn -B verify

test-web:
	cd web && npm ci && npm run test:ci

build-web:
	cd web && npm ci && npx ng build

lint: ## Helm chart + compose file + web production build
	helm lint deploy/helm/medirdv
	helm template medirdv deploy/helm/medirdv > /dev/null
	docker compose config -q
	cd web && npm ci && npx ng build

seed: ## Adds a few extra demo patients through the API (demo users and practitioners are loaded by Flyway)
	scripts/seed.sh

demo: ## Runs the booking saga end to end with curl
	scripts/demo-saga.sh

token: ## Prints an access token: make token USER_NAME=dr.martin
	@scripts/token.sh $(or $(USER_NAME),alice)
