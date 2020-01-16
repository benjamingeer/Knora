# Determine this makefile's path.
# Be sure to place this BEFORE `include` directives, if any.
THIS_FILE := $(lastword $(MAKEFILE_LIST))

include vars.mk

#################################
# Documentation targets
#################################

.PHONY: docs-publish
docs-publish: ## build and publish docs
	docker run --rm -it -v $(PWD):/knora -v $(HOME)/.ivy2:/root/.ivy2 -v $(HOME)/.ssh:/root/.ssh daschswiss/sbt-paradox /bin/sh -c "cd /knora && git config --global user.email $(GIT_EMAIL) && sbt docs/ghpagesPushSite"

.PHONY: docs-build
docs-build: ## build the docs
	docker run --rm -v $(PWD):/knora -v $(HOME)/.ivy2:/root/.ivy2 daschswiss/sbt-paradox /bin/sh -c "cd /knora && sbt docs/makeSite"

#################################
# Docker targets
#################################

.PHONY: build-all-scala
build-all-scala: ## build all scala projects
	bazel build //...

## knora-api
.PHONY: build-knora-api-image
build-knora-api-image: ## build and publish knora-api docker image locally
	bazel run //docker/knora-api

.PHONY: publish-knora-api
publish-knora-api-image: ## publish knora-api image to Dockerhub
	docker run //docker/knora-api:push

## knora-graphdb-se
.PHONY: build-knora-graphdb-se-image
build-knora-graphdb-se-image: ## build and publish knora-graphdb-se docker image locally
	bazel run //docker/knora-graphdb-se

.PHONY: publish-knora-graphdb-se-image
publish-knora-graphdb-se-image: ## publish knora-graphdb-se image to Dockerhub
	bazel run //docker/knora-graphdb-se:push

## knora-graphdb-free
.PHONY: build-knora-graphdb-free-image
build-knora-graphdb-free-image: ## build and publish knora-graphdb-free docker image locally
	bazel run //docker/knora-graphdb-free

.PHONY: publish-knora-graphdb-free-image
publish-knora-graphdb-free-image: ## publish knora-graphdb-se image to Dockerhub
	bazel run //docker/knora-graphdb-free:push

## knora-sipi
.PHONY: build-knora-sipi-image
build-knora-sipi-image: ## build and publish knora-sipi docker image locally
	bazel run //docker/knora-sipi

.PHONY: publish-knora-sipi-image
publish-knora-sipi-image: ## publish knora-sipi image to Dockerhub
	bazel run //docker/knora-sipi:push

## knora-salsah1
.PHONY: build-knora-salsah1-image
build-knora-salsah1-image: ## build and publish knora-salsah1 docker image locally
	bazel run //docker/knora-salsah1

.PHONY: publish-knora-salsah1-image
publish-knora-salsah1-image: build-knora-salsah1-image ## publish knora-salsah1 image to Dockerhub
	bazel run //docker/knora-salsah1:push

## knora-upgrade
.PHONY: build-knora-upgrade-image
build-knora-upgrade-image: build-all-scala ## build and publish knora-upgrade docker image locally
	bazel run //docker/knora-upgrade

.PHONY: publish-knora-upgrade-image
publish-knora-upgrade-image: build-knora-upgrade-image ## publish knora-upgrade image to Dockerhub
	bazel run //docker/knora-upgrade:push

## all images
.PHONY: build-all-images
build-all-images: build-knora-api-image build-knora-graphdb-se-image build-knora-graphdb-free-image build-knora-sipi-image build-knora-salsah1-image build-knora-upgrade-image  ## build all Docker images

.PHONY: publish-all-images
publish-all-images: publish-knora-api-image publish-knora-graphdb-se-image publish-knora-graphdb-free-image publish-knora-sipi-image publish-knora-salsah1-image publish-knora-upgrade-image ## publish all Docker images

#################################
## Docker-Compose targets
#################################

.PHONY: print-env-file
print-env-file: env-file ## prints the env file used by knora-stack
	@cat .env

.PHONY: env-file
env-file: ## write the env file used by knora-stack.
# FIXME: Generate .env file though bazel
ifeq ($(KNORA_GDB_LICENSE), unknown)
	$(warning No GraphDB-SE license set. Using GraphDB-Free.)
	@echo KNORA_GRAPHDB_IMAGE=$(KNORA_GRAPHDB_FREE_IMAGE) > .env
	@echo KNORA_GDB_LICENSE_FILE=no-license >> .env
	@echo KNORA_GDB_TYPE=graphdb-free >> .env
else
	@echo KNORA_GRAPHDB_IMAGE=$(KNORA_GRAPHDB_SE_IMAGE) > .env
	@echo KNORA_GDB_LICENSE_FILE=$(KNORA_GDB_LICENSE) >> .env
	@echo KNORA_GDB_TYPE=graphdb-se >> .env
endif
ifeq ($(KNORA_GDB_IMPORT), unknown)
	$(warning The path to the GraphDB import directory is not set. Using docker volume: db-import.)
	@echo KNORA_GDB_IMPORT_DIR=db-import >> .env
else
	@echo KNORA_GDB_IMPORT_DIR=$(KNORA_GDB_IMPORT) >> .env
endif
ifeq ($(KNORA_GDB_HOME), unknown)
	$(warning The path to the GraphDB home directory is not set. Using docker volume: db-home.)
	@echo KNORA_GDB_HOME_DIR=db-home >> .env
else
	@echo KNORA_GDB_HOME_DIR=$(KNORA_GDB_HOME) >> .env
endif
	@echo KNORA_GDB_HEAP_SIZE=$(KNORA_GDB_HEAP_SIZE) >> .env
	@echo KNORA_SIPI_IMAGE=$(KNORA_SIPI_IMAGE) >> .env
	@echo KNORA_API_IMAGE=$(KNORA_API_IMAGE) >> .env
	@echo KNORA_SALSAH1_IMAGE=$(KNORA_SALSAH1_IMAGE) >> .env
	@echo DOCKERHOST=$(DOCKERHOST) >> .env
    @echo KNORA_WEBAPI_DB_CONNECTIONS=$(KNORA_WEBAPI_DB_CONNECTIONS) >> .env

#################################
## Knora Stack Targets
#################################

.PHONY: stack-up
stack-up: build-all-images env-file ## starts the knora-stack: graphdb, sipi, redis, api, salsah1.
	docker-compose -f docker-compose.yml up -d

.PHONY: stack-restart
stack-restart: stack-up ## re-starts the knora-stack: graphdb, sipi, redis, api, salsah1.
	docker-compose -f docker-compose.yml restart

.PHONY: stack-restart-api
stack-restart-api: ## re-starts the api. Usually used after loading data into GraphDB.
	docker-compose -f docker-compose.yml restart api

.PHONY: stack-logs
stack-logs: ## prints out and follows the logs of the running knora-stack.
	docker-compose -f docker-compose.yml logs -f

.PHONY: stack-logs-db
stack-logs-db: ## prints out and follows the logs of the 'db' container running in knora-stack.
	docker-compose -f docker-compose.yml logs -f db

.PHONY: stack-logs-sipi
stack-logs-sipi: ## prints out and follows the logs of the 'sipi' container running in knora-stack.
	docker-compose -f docker-compose.yml logs -f sipi

.PHONY: stack-logs-sipi-no-follow
stack-logs-sipi-no-follow: ## prints out the logs of the 'sipi' container running in knora-stack.
	docker-compose -f docker-compose.yml logs sipi

.PHONY: stack-logs-redis
stack-logs-redis: ## prints out and follows the logs of the 'redis' container running in knora-stack.
	docker-compose -f docker-compose.yml logs -f redis

.PHONY: stack-logs-api
stack-logs-api: ## prints out and follows the logs of the 'api' container running in knora-stack.
	docker-compose -f docker-compose.yml logs -f api

.PHONY: stack-logs-salsah1
stack-logs-salsah1: ## prints out and follows the logs of the 'salsah1' container running in knora-stack.
	docker-compose -f docker-compose.yml logs -f salsah1

.PHONY: stack-down
stack-down: ## stops the knora-stack.
	docker-compose -f docker-compose.yml down

.PHONY: stack-without-api
stack-without-api: stack-up ## starts the knora-stack without knora-api: graphdb, sipi, redis, salsah1.
	docker-compose -f docker-compose.yml stop api

.PHONY: stack-without-api-and-sipi
stack-without-api-and-sipi: stack-up ## starts the knora-stack without knora-api and sipi: graphdb, redis, salsah1.
	docker-compose -f docker-compose.yml stop api
	docker-compose -f docker-compose.yml stop sipi

.PHONY: stack-without-api-and-salsah1
stack-without-api-and-salsah1: stack-up ## starts the knora-stack without knora-api and salsah1: graphdb, redis, sipi.
	docker-compose -f docker-compose.yml stop api
	docker-compose -f docker-compose.yml stop salsah1

.PHONY: graphdb-up
sipi-up: stack-up ## starts only GraphDB.
	docker-compose -f docker-compose.yml stop api
	docker-compose -f docker-compose.yml stop salsah1
	docker-compose -f docker-compose.yml stop sipi
	docker-compose -f docker-compose.yml stop redis

#################################
## Test Targets
#################################

.PHONY: unit-tests
unit-tests: stack-without-api init-db-test-unit ## runs the unit tests (equivalent to 'sbt webapi/testOnly -- -l org.knora.webapi.testing.tags.E2ETest').
	docker run 	--rm \
				-v /tmp:/tmp \
				-v $(PWD):/src \
				-v $(HOME)/.ivy2:/root/.ivy2 \
				--name=api \
				-e KNORA_WEBAPI_TRIPLESTORE_HOST=db \
				-e KNORA_WEBAPI_SIPI_EXTERNAL_HOST=sipi \
				-e KNORA_WEBAPI_SIPI_INTERNAL_HOST=sipi \
				-e KNORA_WEBAPI_CACHE_SERVICE_REDIS_HOST=redis \
				-e SBT_OPTS="-Xms2048M -Xmx2048M -Xss6M" \
				--network=docker_knora-net \
				daschswiss/scala-sbt sbt 'webapi/testOnly -- -l org.knora.webapi.testing.tags.E2ETest'

.PHONY: unit-tests-with-coverage
unit-tests-with-coverage: stack-without-api ## runs the unit tests (equivalent to 'sbt webapi/testOnly -- -l org.knora.webapi.testing.tags.E2ETest') with code-coverage reporting.
	@echo $@  # print target name
	@sleep 5
	@$(MAKE) -f $(THIS_FILE) init-db-test-unit
	docker run 	--rm \
				-v /tmp:/tmp \
				-v $(PWD):/src/workspace \
				-w /src/workspace \
				-v $(HOME)/.ivy2:/root/.ivy2 \
				--name=api \
				-e KNORA_WEBAPI_TRIPLESTORE_HOST=db \
				-e KNORA_WEBAPI_SIPI_EXTERNAL_HOST=sipi \
				-e KNORA_WEBAPI_SIPI_INTERNAL_HOST=sipi \
				-e KNORA_WEBAPI_CACHE_SERVICE_REDIS_HOST=redis \
				-e SBT_OPTS="-Xms2048M -Xmx2048M -Xss6M" \
				--network=docker_knora-net \
				l.gcr.io/google/bazel:1.2.1 \
				test //webapi:unit_tests

.PHONY: e2e-tests
e2e-tests: stack-without-api init-db-test-unit ## runs the e2e tests (equivalent to 'sbt webapi/testOnly -- -n org.knora.webapi.testing.tags.E2ETest').
	docker run 	--rm \
				-v /tmp:/tmp \
				-v $(PWD):/src \
				-v $(HOME)/.ivy2:/root/.ivy2 \
				--name=api \
				-e KNORA_WEBAPI_TRIPLESTORE_HOST=db \
				-e KNORA_WEBAPI_SIPI_EXTERNAL_HOST=sipi \
				-e KNORA_WEBAPI_SIPI_INTERNAL_HOST=sipi \
				-e KNORA_WEBAPI_CACHE_SERVICE_REDIS_HOST=redis \
				-e SBT_OPTS="-Xms2048M -Xmx2048M -Xss6M" \
				--network=docker_knora-net \
				daschswiss/scala-sbt sbt 'webapi/testOnly -- -n org.knora.webapi.testing.tags.E2ETest'

.PHONY: e2e-tests-with-coverage
e2e-tests-with-coverage: stack-without-api ## runs the e2e tests (equivalent to 'sbt webapi/testOnly -- -n org.knora.webapi.testing.tags.E2ETest') with code-coverage reporting.
	@echo $@  # print target name
	@sleep 5
	@$(MAKE) -f $(THIS_FILE) init-db-test-unit
	docker run 	--rm \
				-v /tmp:/tmp \
				-v $(PWD):/src/workspace \
				-w /src/workspace \
				-v $(HOME)/.ivy2:/root/.ivy2 \
				--name=api \
				-e KNORA_WEBAPI_TRIPLESTORE_HOST=db \
				-e KNORA_WEBAPI_SIPI_EXTERNAL_HOST=sipi \
				-e KNORA_WEBAPI_SIPI_INTERNAL_HOST=sipi \
				-e KNORA_WEBAPI_CACHE_SERVICE_REDIS_HOST=redis \
				-e SBT_OPTS="-Xms2048M -Xmx2048M -Xss6M" \
				--network=docker_knora-net \
				l.gcr.io/google/bazel:1.2.1 \
				test //webapi:e2e_tests

.PHONY: it-tests
it-tests: stack-without-api init-db-test-unit ## runs the integration tests (equivalent to 'sbt webapi/it').
	docker run 	--rm \
				-v /tmp:/tmp \
				-v $(PWD):/src \
				-v $(HOME)/.ivy2:/root/.ivy2 \
				--name=api \
				-e KNORA_WEBAPI_TRIPLESTORE_HOST=db \
				-e KNORA_WEBAPI_SIPI_EXTERNAL_HOST=sipi \
				-e KNORA_WEBAPI_SIPI_INTERNAL_HOST=sipi \
				-e KNORA_WEBAPI_CACHE_SERVICE_REDIS_HOST=redis \
				-e SBT_OPTS="-Xms2048M -Xmx2048M -Xss6M" \
				--network=docker_knora-net \
				daschswiss/scala-sbt sbt 'webapi/it:test'

.PHONY: test-salsah1
test-salsah1: stack-up ## runs salsah1 headless browser tests
	@echo $@  # print target name
	@sleep 5
	@$(MAKE) -f $(THIS_FILE) init-db-test-minimal
	bazel test //salsah1/...

.PHONY: test-upgrade
test-upgrade: stack-down ## runs upgrade tests
	@echo $@  # print target name
	bazel test //upgrade/...

.PHONY: test-webapi
test-webapi: stack-without-api ## runs webapi tests
	@echo $@  # print target name
	@sleep 5
	@$(MAKE) -f $(THIS_FILE) init-db-test-unit
	bazel test //webapi/...

.PHONY: test
test:  ## runs all tests.
	@$(MAKE) -f $(THIS_FILE) test-webapi
	@$(MAKE) -f $(THIS_FILE) test-upgrade
	@$(MAKE) -f $(THIS_FILE) test-salsah1

#################################
## Database Management
#################################

.PHONY: init-db-test
init-db-test: ## initializes the knora-test repository
	$(MAKE) -C webapi/scripts graphdb-se-docker-init-knora-test

.PHONY: init-db-test-minimal
init-db-test-minimal: ## initializes the knora-test repository with minimal data
	$(MAKE) -C webapi/scripts graphdb-se-docker-init-knora-test-minimal

.PHONY: init-db-test-unit
init-db-test-unit: ## initializes the knora-test-unit repository
	$(MAKE) -C webapi/scripts graphdb-se-docker-init-knora-test-unit

.PHONY: init-db-test-free
init-db-test-free: ## initializes the knora-test repository (for GraphDB-Free)
	$(MAKE) -C webapi/scripts graphdb-free-docker-init-knora-test-free

.PHONY: init-db-test-minimal-free
init-db-test-minimal-free: ## initializes the knora-test repository with minimal data (for GraphDB-Free)
	$(MAKE) -C webapi/scripts graphdb-free-docker-init-knora-test-minimal

.PHONY: init-db-test-unit-free
init-db-test-unit-free: ## initializes the knora-test-unit repository (for GraphDB-Free)
	$(MAKE) -C webapi/scripts graphdb-free-docker-init-knora-test-unit

.PHONY: init-db-test-local
init-db-test-local: ## initializes the knora-test-unit repository (for a local GraphDB-SE)
	$(MAKE) -C webapi/scripts graphdb-se-local-init-knora-test

.PHONY: init-db-test-unit-local
init-db-test-unit-local: ## initializes the knora-test-unit repository (for a local GraphDB-SE)
	$(MAKE) -C webapi/scripts graphdb-se-local-init-knora-test-unit

clean: ## clean build artifacts
	@rm -rf .env
	@bazel clean
	@sbt clean

clean-docker: ## cleans the docker installation
	docker system prune -af

.PHONY: info
info: ## print out all variables
	@echo "GIT_EMAIL: \t\t\t $(GIT_EMAIL)"
	@echo "KNORA_API_IMAGE: \t\t $(KNORA_API_IMAGE)"
	@echo "KNORA_GRAPHDB_SE_IMAGE: \t $(KNORA_GRAPHDB_SE_IMAGE)"
	@echo "KNORA_GRAPHDB_FREE_IMAGE: \t $(KNORA_GRAPHDB_FREE_IMAGE)"
	@echo "KNORA_SIPI_IMAGE: \t\t $(KNORA_SIPI_IMAGE)"
	@echo "KNORA_UPGRADE_IMAGE: \t\t $(KNORA_UPGRADE_IMAGE)"
	@echo "KNORA_SALSAH1_IMAGE: \t\t $(KNORA_SALSAH1_IMAGE)"
	@echo "KNORA_GDB_LICENSE: \t\t $(KNORA_GDB_LICENSE)"
	@echo "KNORA_GDB_IMPORT: \t\t $(KNORA_GDB_IMPORT)"
	@echo "KNORA_GDB_HOME: \t\t $(KNORA_GDB_HOME)"
	@echo "DOCKERHOST: \t\t\t $(DOCKERHOST)"

.PHONY: help
help: ## this help
	@awk 'BEGIN {FS = ":.*?## "} /^[a-zA-Z_-]+:.*?## / {printf "\033[36m%-30s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST) | sort

.DEFAULT_GOAL := help
