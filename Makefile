# Determine this makefile's path.
# Be sure to place this BEFORE `include` directives, if any.
# THIS_FILE := $(lastword $(MAKEFILE_LIST))
THIS_FILE := $(abspath $(lastword $(MAKEFILE_LIST)))
CURRENT_DIR := $(shell dirname $(realpath $(firstword $(MAKEFILE_LIST))))

include vars.mk

#################################
# Documentation targets
#################################

.PHONY: docs-publish
docs-publish: ## build and publish docs
	docker run --rm -it -v $(CURRENT_DIR):/knora -v $(HOME)/.ivy2:/root/.ivy2 -v $(HOME)/.ssh:/root/.ssh daschswiss/sbt-paradox /bin/sh -c "cd /knora && git config --global user.email $(GIT_EMAIL) && sbt docs/ghpagesPushSite"

.PHONY: docs-build
docs-build: ## build the docs
	docker run --rm -v $(CURRENT_DIR):/knora -v $(HOME)/.ivy2:/root/.ivy2 daschswiss/sbt-paradox /bin/sh -c "cd /knora && sbt docs/makeSite"

#################################
# Bazel targets
#################################

.PHONY: bazel-build
bazel-build: ## build everything
	@bazel build //...

#################################
# Docker targets
#################################

.PHONY: docker-build-knora-api-image
docker-build-knora-api-image: # build and publish knora-api docker image locally
	@bazel run //docker/knora-api

.PHONY: docker-publish-knora-api
docker-publish-knora-api-image: # publish knora-api image to Dockerhub
	@docker run //docker/knora-api:push

.PHONY: docker-build-knora-jena-fuseki-image
docker-build-knora-jena-fuseki-image: # build and publish knora-jena-fuseki docker image locally
	@bazel run //docker/knora-jena-fuseki

.PHONY: docker-publish-knora-jena-fuseki-image
docker-publish-knora-jena-fuseki-image: # publish knora-jena-fuseki image to Dockerhub
	@bazel run //docker/knora-jena-fuseki:push

.PHONY: docker-build-knora-sipi-image
docker-build-knora-sipi-image: # build and publish knora-sipi docker image locally
	@bazel run //docker/knora-sipi

.PHONY: docker-publish-knora-sipi-image
docker-publish-knora-sipi-image: # publish knora-sipi image to Dockerhub
	@bazel run //docker/knora-sipi:push

.PHONY: docker-build-knora-salsah1-image
docker-build-knora-salsah1-image: # build and publish knora-salsah1 docker image locally
	@bazel run //docker/knora-salsah1

.PHONY: docker-publish-knora-salsah1-image
docker-publish-knora-salsah1-image: # publish knora-salsah1 image to Dockerhub
	@bazel run //docker/knora-salsah1:push

.PHONY: docker-build
docker-build: docker-build-knora-api-image docker-build-knora-jena-fuseki-image docker-build-knora-sipi-image docker-build-knora-salsah1-image ## build and publish all Docker images locally

.PHONY: docker-publish
docker-publish: docker-publish-knora-api-image docker-publish-knora-jena-fuseki-image docker-publish-knora-sipi-image docker-publish-knora-salsah1-image ## publish all Docker images to Dockerhub

#################################
## Docker-Compose targets
#################################

.PHONY: print-env-file
print-env-file: ## prints the env file used by knora-stack
	@cat .env

.PHONY: env-file
env-file: ## write the env file used by knora-stack.
ifeq ($(KNORA_DB_HOME), unknown)
	@echo KNORA_DB_HOME_DIR=db-home > .env
else
	$(info Using $(KNORA_DB_HOME) for the DB home directory.)
	@echo KNORA_DB_HOME_DIR=$(KNORA_DB_HOME) > .env
endif
ifeq ($(KNORA_DB_IMPORT), unknown)
	@echo KNORA_DB_IMPORT_DIR=db-import >> .env
else
	$(info Using $(KNORA_DB_IMPORT) for the DB import directory.)
	@echo KNORA_DB_IMPORT_DIR=$(KNORA_DB_IMPORT) >> .env
endif
	@echo KNORA_SIPI_IMAGE=$(KNORA_SIPI_IMAGE) >> .env
	@echo KNORA_FUSEKI_IMAGE=$(KNORA_FUSEKI_IMAGE) >> .env
	@echo FUSEKI_HEAP_SIZE=$(FUSEKI_HEAP_SIZE) >> .env
	@echo KNORA_API_IMAGE=$(KNORA_API_IMAGE) >> .env
	@echo KNORA_SALSAH1_IMAGE=$(KNORA_SALSAH1_IMAGE) >> .env
	@echo DOCKERHOST=$(DOCKERHOST) >> .env
	@echo KNORA_WEBAPI_DB_CONNECTIONS=$(KNORA_WEBAPI_DB_CONNECTIONS) >> .env
	@echo KNORA_DB_REPOSITORY_NAME=$(KNORA_DB_REPOSITORY_NAME) >> .env
	@echo LOCAL_HOME=$(CURRENT_DIR) >> .env

#################################
## Knora Stack Targets
#################################

.PHONY: stack-up
stack-up: docker-build env-file ## starts the knora-stack: fuseki, sipi, redis, api, salsah1.
	docker-compose -f docker-compose.yml up -d db
	$(CURRENT_DIR)/webapi/scripts/wait-for-db.sh
	docker-compose -f docker-compose.yml up -d

.PHONY: stack-up-ci
stack-up-ci: KNORA_DB_REPOSITORY_NAME := knora-test-unit
stack-up-ci: docker-build env-file print-env-file ## starts the knora-stack using 'knora-test-unit' repository: graphdb, sipi, redis, api, salsah1.
	docker-compose -f docker-compose.yml up -d

.PHONY: stack-restart
stack-restart: stack-up ## re-starts the knora-stack: graphdb, sipi, redis, api, salsah1.
	@docker-compose -f docker-compose.yml restart

.PHONY: stack-restart-api
stack-restart-api: ## re-starts the api. Usually used after loading data into GraphDB.
	docker-compose -f docker-compose.yml restart api
	@$(CURRENT_DIR)/webapi/scripts/wait-for-knora.sh

.PHONY: stack-logs
stack-logs: ## prints out and follows the logs of the running knora-stack.
	@docker-compose -f docker-compose.yml logs -f

.PHONY: stack-logs-db
stack-logs-db: ## prints out and follows the logs of the 'db' container running in knora-stack.
	@docker-compose -f docker-compose.yml logs -f db

.PHONY: stack-logs-db-no-follow
stack-logs-db-no-follow: ## prints out the logs of the 'db' container running in knora-stack.
	docker-compose -f docker-compose.yml logs db

.PHONY: stack-logs-sipi
stack-logs-sipi: ## prints out and follows the logs of the 'sipi' container running in knora-stack.
	@docker-compose -f docker-compose.yml logs -f sipi

.PHONY: stack-logs-sipi-no-follow
stack-logs-sipi-no-follow: ## prints out the logs of the 'sipi' container running in knora-stack.
	@docker-compose -f docker-compose.yml logs sipi

.PHONY: stack-logs-redis
stack-logs-redis: ## prints out and follows the logs of the 'redis' container running in knora-stack.
	@docker-compose -f docker-compose.yml logs -f redis

.PHONY: stack-logs-api
stack-logs-api: ## prints out and follows the logs of the 'api' container running in knora-stack.
	@docker-compose -f docker-compose.yml logs -f api

.PHONY: stack-logs-api-no-follow
stack-logs-api-no-follow: ## prints out the logs of the 'api' container running in knora-stack.
	docker-compose -f docker-compose.yml logs api

.PHONY: stack-logs-salsah1
stack-logs-salsah1: ## prints out and follows the logs of the 'salsah1' container running in knora-stack.
	docker-compose -f docker-compose.yml logs -f salsah1

.PHONY: stack-health
stack-health:
	curl -f 0.0.0.0:3333/health

.PHONY: stack-status
stack-status:
	docker-compose -f docker-compose.yml ps

.PHONY: stack-down
stack-down: ## stops the knora-stack.
	docker-compose -f docker-compose.yml down

.PHONY: stack-down-delete-volumes
stack-down-delete-volumes: ## stops the knora-stack and delete any created volumes (deletes the database!).
	docker-compose -f docker-compose.yml down --volumes

.PHONY: stack-config
stack-config: env-file
	docker-compose -f docker-compose.yml config

## stack without api
.PHONY: stack-without-api
stack-without-api: stack-up ## starts the knora-stack without knora-api: graphdb, sipi, redis, salsah1.
	@docker-compose -f docker-compose.yml stop api

.PHONY: stack-without-api-and-sipi
stack-without-api-and-sipi: stack-up ## starts the knora-stack without knora-api and sipi: graphdb, redis, salsah1.
	@docker-compose -f docker-compose.yml stop api
	@docker-compose -f docker-compose.yml stop sipi

.PHONY: stack-without-api-and-salsah1
stack-without-api-and-salsah1: stack-up ## starts the knora-stack without knora-api and salsah1: graphdb, redis, sipi.
	@docker-compose -f docker-compose.yml stop api
	@docker-compose -f docker-compose.yml stop salsah1

.PHONY: stack-db-only
stack-db-only: stack-up ## starts only GraphDB.
	@docker-compose -f docker-compose.yml stop api
	@docker-compose -f docker-compose.yml stop salsah1
	@docker-compose -f docker-compose.yml stop sipi
	@docker-compose -f docker-compose.yml stop redis

.PHONY: stack-down
stack-down: ## stops the knora-stack.
	@docker-compose -f docker-compose.yml down

#################################
## Test Targets
#################################

.PHONY: test-webapi
test-webapi: ## runs all webapi tests.
	bazel test //webapi/...

.PHONY: test-unit
test-unit: ## runs the dsp-api unit tests.
	bazel test //webapi/... --deleted_packages=//webapi/src/test/scala/org/knora/webapi/e2e

.PHONY: test-e2e
test-e2e: ## runs the dsp-api e2e tests.
	bazel test //webapi/src/test/scala/org/knora/webapi/e2e/...

.PHONY: test-it
test-it: ## runs the dsp-api integration tests.
	bazel test //webapi:it_tests

.PHONY: test-salsah1
test-salsah1: ## runs salsah1 headless browser tests
	bazel test //salsah1/...

.PHONY: test-upgrade-integration
test-upgrade-integration: stack-down-delete-volumes stack-db-only ## runs upgrade integration test
	@$(MAKE) -f $(THIS_FILE) init-db-test-minimal
	# unzip test data
	@mkdir -p $(PWD)/.tmp/test_data/v7.0.0/
	@unzip $(PWD)/test_data/v7.0.0/v7.0.0-knora-test.trig.zip -d $(PWD)/.tmp/test_data/v7.0.0/
	@rm -rf /tmp/knora-test-data/v7.0.0/
	# empty repository
	$(CURRENT_DIR)/webapi/scripts/fuseki-empty-repository.sh -r knora-test -u gaga -p gaga -h localhost:3030
	# load v7.0.0 data
	$(CURRENT_DIR)/webapi/scripts/fuseki-upload-repository.sh -r knora-test -u gaga -p gaga -h localhost:3030 $(PWD)/.tmp/knora-test-data/v7.0.0/v7.0.0-knora-test.trig
	# call target which restarts the API and emits error if API does not start after a certain time
	@$(MAKE) -f $(THIS_FILE) stack-restart-api
	@$(MAKE) -f $(THIS_FILE) stack-logs-api-no-follow

.PHONY: test-repository-update
test-repository-update: init-db-test-minimal
	@rm -rf /tmp/knora-test-data/v7.0.0/
	@mkdir -p /tmp/knora-test-data/v7.0.0/
	@unzip $(CURRENT_DIR)/test-data/v7.0.0/v7.0.0-knora-test.trig.zip -d /tmp/knora-test-data/v7.0.0/
	$(CURRENT_DIR)/webapi/scripts/fuseki-empty-repository.sh -r knora-test -u admin -p test -h localhost:3030
	$(CURRENT_DIR)/webapi/scripts/fuseki-upload-repository.sh -r knora-test -u admin -p test -h localhost:3030 /tmp/knora-test-data/v7.0.0/v7.0.0-knora-test.trig
	@$(MAKE) -f $(THIS_FILE) stack-restart-api
	@$(MAKE) -f $(THIS_FILE) stack-logs-api-no-follow

.PHONY: test
test:  ## runs all test targets.
	bazel test //...

#################################
## Database Management
#################################

.PHONY: init-db-test
init-db-test: stack-down-delete-volumes stack-without-api ## initializes the knora-test repository
	@echo $@
	@$(MAKE) -C webapi/scripts fuseki-init-knora-test

.PHONY: init-db-test-minimal
init-db-test-minimal: stack-down-delete-volumes stack-without-api ## initializes the knora-test repository with minimal data
	@echo $@
	@$(MAKE) -C webapi/scripts fuseki-init-knora-test-minimal

.PHONY: init-db-test-unit
init-db-test-unit: stack-down-delete-volumes stack-without-api ## initializes the knora-test-unit repository
	@echo $@
	@$(MAKE) -C webapi/scripts fuseki-init-knora-test-unit

.PHONY: init-db-test-unit-minimal
init-db-test-unit-minimal: stack-down-delete-volumes stack-without-api ## initializes the knora-test-unit repository with minimal data
	@echo $@
	@$(MAKE) -C webapi/scripts fuseki-init-knora-test-unit-minimal

#################################
## Other
#################################

.PHONY: clean-local-tmp
clean-local-tmp:
	@rm -rf .tmp
	@mkdir .tmp

clean: ## clean build artifacts
	@rm -rf .env
	@bazel clean
	@sbt clean
	@rm -rf .tmp

clean-docker: ## cleans the docker installation
	@docker system prune -af

.PHONY: info
info: ## print out all variables
	@echo "BUILD_TAG: \t\t $(BUILD_TAG)"
	@echo "GIT_EMAIL: \t\t $(GIT_EMAIL)"
	@echo "SIPI_VERSION: \t\t $(SIPI_VERSION)"
	@echo "KNORA_API_IMAGE: \t $(KNORA_API_IMAGE)"
	@echo "KNORA_FUSEKI_IMAGE: \t $(KNORA_FUSEKI_IMAGE)"
	@echo "KNORA_SIPI_IMAGE: \t $(KNORA_SIPI_IMAGE)"
	@echo "KNORA_ASSETS_IMAGE: \t $(KNORA_ASSETS_IMAGE)"
	@echo "KNORA_SALSAH1_IMAGE: \t $(KNORA_SALSAH1_IMAGE)"
	@echo "KNORA_DB_IMPORT: \t $(KNORA_DB_IMPORT)"
	@echo "KNORA_DB_HOME: \t\t $(KNORA_DB_HOME)"

.PHONY: help
help: ## this help
	@awk 'BEGIN {FS = ":.*?## "} /^[a-zA-Z_-]+:.*?## / {printf "\033[36m%-30s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST) | sort

.DEFAULT_GOAL := help
