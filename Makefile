# Determine this makefile's path.
# Be sure to place this BEFORE `include` directives, if any.
# THIS_FILE := $(lastword $(MAKEFILE_LIST))
THIS_FILE := $(abspath $(lastword $(MAKEFILE_LIST)))
CURRENT_DIR := $(shell dirname $(realpath $(firstword $(MAKEFILE_LIST))))

include vars.mk

#################################
# Documentation targets
#################################
SBTX = ./sbtx

.PHONY: docs-build
docs-build: ## build docs into the local 'site' folder
	@$(MAKE) -C docs graphvizfigures
	mkdocs build

.PHONY: docs-serve
docs-serve: ## serve docs for local viewing
	@$(MAKE) -C docs graphvizfigures
	mkdocs serve

.PHONY: docs-install-requirements
docs-install-requirements: ## install requirements
	pip3 install -r docs/requirements.txt

.PHONY: docs-clean
docs-clean: ## cleans the project directory
	@rm -rf site/

.PHONY: structurizer
structurizer: ## starts the structurizer and serves c4 architecture docs
	@docker pull structurizr/lite
	@docker run -it --rm -p 8080:8080 -v $(CURRENT_DIR)/docs/architecture:/usr/local/structurizr structurizr/lite

#################################
# Docker targets
#################################

.PHONY: build
build: docker-build ## build all targets (excluding docs)

# add DOCKER_BUILDKIT=1 to enable buildkit logging as info
# https://github.com/sbt/sbt-native-packager/issues/1371

.PHONY: docker-build-dsp-api-image
docker-build-dsp-api-image: # build and publish dsp-api docker image locally
	export DOCKER_BUILDKIT=1; $(SBTX) "webapi / Docker / publishLocal"

.PHONY: docker-publish-dsp-api-image
docker-publish-dsp-api-image: # publish dsp-api image to Dockerhub
	export DOCKER_BUILDKIT=1; $(SBTX) "webapi / Docker / publish"

.PHONY: docker-build-sipi-image
docker-build-sipi-image: # build and publish sipi docker image locally
	 export DOCKER_BUILDKIT=1; $(SBTX) "sipi / Docker / publishLocal"

.PHONY: docker-publish-sipi-image
docker-publish-sipi-image: # publish sipi image to Dockerhub
	export DOCKER_BUILDKIT=1; $(SBTX) "sipi / Docker / publish"

.PHONY: docker-build
docker-build: docker-build-dsp-api-image docker-build-sipi-image ## build and publish all Docker images locally

.PHONY: docker-publish
docker-publish: docker-publish-dsp-api-image docker-publish-sipi-image ## publish all Docker images to Dockerhub

.PHONY: docker-image-tag
docker-image-tag: ## prints the docker image tag
	@$(SBTX) -Dsbt.log.noformat=true -Dsbt.supershell=false -Dsbt.ci=true -error "print dockerImageTag"

#################################
## Docker-Compose targets
#################################

.PHONY: print-env-file
print-env-file: ## prints the env file used by dsp-stack
	@cat .env

.PHONY: env-file
env-file: ## write the env file used by dsp-stack.
	@echo DOCKERHOST=$(DOCKERHOST) > .env
	@echo KNORA_DB_REPOSITORY_NAME=$(KNORA_DB_REPOSITORY_NAME) >> .env
	@echo LOCAL_HOME=$(CURRENT_DIR) >> .env

#################################
## DSP Stack Targets
#################################

.PHONY: stack-up
stack-up: docker-build env-file ## starts the dsp-stack: fuseki, sipi, api and app.
	@docker compose -f docker-compose.yml up -d db
	$(CURRENT_DIR)/webapi/scripts/wait-for-db.sh
	@docker compose -f docker-compose.yml up -d
	$(CURRENT_DIR)/webapi/scripts/wait-for-api.sh

.PHONY: stack-up-fast
stack-up-fast: docker-build-dsp-api-image env-file ## starts the dsp-stack by skipping rebuilding most of the images (only api image is rebuilt).
	docker-compose -f docker-compose.yml up -d

.PHONY: stack-up-ci
stack-up-ci: KNORA_DB_REPOSITORY_NAME := knora-test-unit
stack-up-ci: docker-build env-file print-env-file ## starts the dsp-stack using 'knora-test-unit' repository: fuseki, sipi, api.
	docker-compose -f docker-compose.yml up -d

.PHONY: stack-restart
stack-restart: ## re-starts the dsp-stack: fuseki, sipi, api.
	@docker compose -f docker-compose.yml down
	@docker compose -f docker-compose.yml up -d db
	$(CURRENT_DIR)/webapi/scripts/wait-for-db.sh
	@docker compose -f docker-compose.yml up -d
	$(CURRENT_DIR)/webapi/scripts/wait-for-api.sh

.PHONY: stack-restart-api
stack-restart-api: ## re-starts the api. Usually used after loading data into fuseki.
	docker-compose -f docker-compose.yml restart api
	@$(CURRENT_DIR)/webapi/scripts/wait-for-api.sh

.PHONY: stack-logs
stack-logs: ## prints out and follows the logs of the running dsp-stack.
	@docker compose -f docker-compose.yml logs -f

.PHONY: stack-logs-db
stack-logs-db: ## prints out and follows the logs of the 'db' container running in dsp-stack.
	@docker compose -f docker-compose.yml logs -f db

.PHONY: stack-logs-db-no-follow
stack-logs-db-no-follow: ## prints out the logs of the 'db' container running in dsp-stack.
	@docker-compose -f docker-compose.yml logs db

.PHONY: stack-logs-sipi
stack-logs-sipi: ## prints out and follows the logs of the 'sipi' container running in dsp-stack.
	@docker compose -f docker-compose.yml logs -f sipi

.PHONY: stack-logs-sipi-no-follow
stack-logs-sipi-no-follow: ## prints out the logs of the 'sipi' container running in dsp-stack.
	@docker compose -f docker-compose.yml logs sipi

.PHONY: stack-logs-api
stack-logs-api: ## prints out and follows the logs of the 'api' container running in dsp-stack.
	@docker compose -f docker-compose.yml logs -f api

.PHONY: stack-logs-api-no-follow
stack-logs-api-no-follow: ## prints out the logs of the 'api' container running in dsp-stack.
	@docker compose -f docker-compose.yml logs api

.PHONY: stack-health
stack-health:
	curl -f 0.0.0.0:3333/health

.PHONY: stack-status
stack-status:
	@docker compose -f docker-compose.yml ps

.PHONY: stack-down
stack-down: ## stops the dsp-stack.
	@docker compose -f docker-compose.yml down

.PHONY: stack-down-delete-volumes
stack-down-delete-volumes: clean-local-tmp clean-sipi-tmp ## stops the dsp-stack and deletes any created volumes (deletes the database!).
	@docker compose -f docker-compose.yml down --volumes

.PHONY: stack-config
stack-config: env-file
	@docker compose -f docker-compose.yml config

.PHONY: stack-without-api
stack-without-api: stack-up ## starts the dsp-stack without dsp-api: fuseki and sipi only.
	@docker compose -f docker-compose.yml stop api

.PHONY: stack-without-app
stack-without-app: stack-up ## starts the dsp-stack without dsp-app - this is the previous state of "make stack-up" command.
	@docker compose -f docker-compose.yml stop app

.PHONY: stack-without-api-and-sipi
stack-without-api-and-sipi: stack-up ## starts the dsp-stack without dsp-api and sipi: fuseki only.
	@docker compose -f docker-compose.yml stop api
	@docker compose -f docker-compose.yml stop sipi

.PHONY: stack-db-only
stack-db-only: env-file  ## starts only fuseki.
	@docker compose -f docker-compose.yml up -d db
	$(CURRENT_DIR)/webapi/scripts/wait-for-db.sh

#################################
## Test Targets
#################################

.PHONY: client-test-data
client-test-data: export KNORA_WEBAPI_COLLECT_CLIENT_TEST_DATA := true
client-test-data: build ## runs the dsp-api e2e and r2r tests and generates client-test-data.
	$(CURRENT_DIR)/webapi/scripts/zap-client-test-data.sh
	$(SBTX) -v "integration/testOnly *E2ESpec *E2EZioHttpSpec *R2RSpec"
	$(CURRENT_DIR)/webapi/scripts/zip-client-test-data.sh

.PHONY: test-repository-upgrade
test-repository-upgrade: build init-db-test-minimal ## runs DB upgrade integration test
	@rm -rf $(CURRENT_DIR)/.tmp/knora-test-data/v7.0.0/
	@mkdir -p $(CURRENT_DIR)/.tmp/knora-test-data/v7.0.0/
	@unzip $(CURRENT_DIR)/test_data/v7.0.0/v7.0.0-knora-test.trig.zip -d $(CURRENT_DIR)/.tmp/knora-test-data/v7.0.0/
	# empty repository
	$(CURRENT_DIR)/webapi/scripts/fuseki-empty-repository.sh -r knora-test -u admin -p test -h localhost:3030
	# load v7.0.0 data
	$(CURRENT_DIR)/webapi/scripts/fuseki-upload-repository.sh -r knora-test -u admin -p test -h localhost:3030 $(CURRENT_DIR)/.tmp/knora-test-data/v7.0.0/v7.0.0-knora-test.trig
	# call target which restarts the API and emits error if API does not start
	# after a certain time. at startup, data should be upgraded.
	@$(MAKE) -f $(THIS_FILE) stack-up

.PHONY: test-all
test-all: test integration-test

.PHONY: test
test: ## runs all unit tests
	$(SBTX) -v coverage "webapi/test" coverageAggregate

.PHONY: integration-test
integration-test: docker-build-sipi-image ## runs all integration tests
	$(SBTX) -v coverage "integration/test" coverageAggregate


#################################
## Database Management
#################################

.PHONY: init-db-test
init-db-test: env-file stack-down-delete-volumes stack-db-only ## initializes the knora-test repository
	@echo $@
	cd $(CURRENT_DIR)/webapi/scripts && ./fuseki-init-knora-test.sh

.PHONY: init-db-test-minimal
init-db-test-minimal: env-file stack-down-delete-volumes stack-db-only ## initializes the knora-test repository with minimal data
	@echo $@
	cd $(CURRENT_DIR)/webapi/scripts && ./fuseki-init-knora-test-minimal.sh

.PHONY: init-db-test-empty
init-db-test-empty: env-file stack-down-delete-volumes stack-db-only ## initializes the knora-test repository with minimal data
	@echo $@
	cd $(CURRENT_DIR)/webapi/scripts && ./fuseki-init-knora-test-empty.sh

.PHONY: init-db-from-test
init-db-from-test: ## init local database with data from test server. Use as `make init-db-from-test PW=database-password`
	@echo $@
	${MAKE} init-db-from-env ENV=db.test.dasch.swiss

.PHONY: init-db-from-test-dump
init-db-from-test-dump: ## init local database with data from local dump file of test server. Use as `make init-db-from-test-dump`
	@echo $@
	${MAKE} init-db-from-dump-file DUMP=db.test.dasch.swiss.trig

.PHONY: init-db-from-staging
init-db-from-staging: ## init local database with data from staging server. Use as `make init-db-from-staging PW=database-password`
	@echo $@
	${MAKE} init-db-from-env ENV=db.staging.dasch.swiss

.PHONY: init-db-from-staging-dump
init-db-from-staging-dump: ## init local database with data from local dump file of staging server. Use as `make init-db-from-staging-dump`
	@echo $@
	${MAKE} init-db-from-dump-file DUMP=db.staging.dasch.swiss.trig

.PHONY: init-db-from-prod
init-db-from-prod: ## init local database with data from prod server. Use as `make init-db-from-prod PW=database-password`
	@echo $@
	${MAKE} init-db-from-env ENV=db.dasch.swiss

.PHONY: init-db-from-prod-dump
init-db-from-prod-dump: ## init local database with data from local dump file of prod server. Use as `make init-db-from-prod-dump`
	@echo $@
	${MAKE} init-db-from-dump-file DUMP=db.dasch.swiss.trig

.PHONY: init-db-from-dev
init-db-from-dev: ## init local database with data from dev. Use as `make init-db-from-dev PW=database-password`
	@echo $@
	${MAKE} init-db-from-env ENV=db.dev.dasch.swiss

.PHONY: init-db-from-dev-dump
init-db-from-dev-dump: ## init local database with data from local dump file of dev server. Use as `make init-db-from-dev-dump`
	@echo $@
	${MAKE} init-db-from-dump-file DUMP=db.dev.dasch.swiss.trig

.PHONY: init-db-from-ls-test-server
init-db-from-ls-test-server: ## init local database with data from ls-test-server. Use as `make init-db-from-ls-test-server PW=database-password`
	@echo $@
	${MAKE} init-db-from-env ENV=db.ls-test-server.dasch.swiss

.PHONY: init-db-from-ls-test-server-dump
init-db-from-ls-test-server-dump: ## init local database with data from local dump file of ls-test-server server. Use as `make init-db-from-ls-test-server-dump`
	@echo $@
	${MAKE} init-db-from-dump-file DUMP=db.ls-test-server.dasch.swiss.trig

.PHONY: db-dump
db-dump: ## Dump data from an env. Use as `make db_dump PW=database-password ENV=db.0000-test-server.dasch.swiss`
	@echo $@
	@echo dumping environment ${ENV}
	@curl -X GET -H "Accept: application/trig" -u "admin:${PW}" "https://${ENV}/dsp-repo" > "${ENV}.trig"

.PHONY: init-db-from-dump-file
init-db-from-dump-file: ## init local database from a specified dump file. Use as `make init-db-from-dump-file DUMP=some-dump-file.trig`
	@echo $@
	@echo dump file: ${DUMP}
	${MAKE} init-db-test-empty
	@curl -X POST -H "Content-Type: application/sparql-update" -d "DROP ALL" -u "admin:test" "http://localhost:3030/knora-test"
	@curl -X POST -H "Content-Type: application/trig" -T "${CURRENT_DIR}/${DUMP}" -u "admin:test" "http://localhost:3030/knora-test"

.PHONY: init-db-from-env
init-db-from-env: ## ## Dump data from an env and upload it to the local DB. Use as `make init-db-from-env PW=database-password ENV=db.0000-test-server.dasch.swiss`
	@echo $@
	${MAKE} db-dump
	${MAKE} init-db-from-dump-file DUMP=${ENV}.trig


#################################
## Other
#################################

clean-docker: ## cleans the docker installation
	@docker system prune -af
	@docker volume prune -f

.PHONY: clean-local-tmp
clean-local-tmp:
	@rm -rf .tmp
	@mkdir .tmp

.PHONY: clean-metals
clean-metals: ## clean SBT and Metals related stuff
	@rm -rf .bloop
	@rm -rf .bsp
	@rm -rf .metals
	@rm -rf target
	@$(SBTX) "clean"


.PHONY: clean
clean: docs-clean clean-local-tmp clean-docker clean-sipi-tmp ## clean build artifacts
	@rm -rf .env

.PHONY: clean-sipi-tmp
clean-sipi-tmp: ## deletes all files in Sipi's tmp folder
	@mkdir empty_folder_for_clean_sipi_tmp
	@cp sipi/images/tmp/.gitignore empty_folder_for_clean_sipi_tmp/.gitignore
	@rsync -a --delete empty_folder_for_clean_sipi_tmp/ sipi/images/tmp/ # use rsync because it can handle large number of files
	@rm -r empty_folder_for_clean_sipi_tmp

.PHONY: clean-sipi-projects
clean-sipi-projects: ## deletes all files uploaded within a project
	@rm -rf sipi/images/[0-9A-F][0-9A-F][0-9A-F][0-9A-F]

.PHONY: check
check: ## Run code formatting check 
	@$(SBTX) "check"

.PHONY: fmt
fmt: ## Run code formatting fix
	@$(SBTX) "fmt"


.PHONY: help
help: ## this help
	@awk 'BEGIN {FS = ":.*?## "} /^[a-zA-Z_-]+:.*?## / {printf "\033[36m%-30s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST) | sort

.DEFAULT_GOAL := help
