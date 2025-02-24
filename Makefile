# Graphviz diagrams to be converted to PNG
DOT_FIGURES = $(shell find ./ -type f -name '*.dot')
PNG_FIGURES = $(patsubst %.dot,%.dot.png,$(DOT_FIGURES))

.PHONY: clean
clean: ## to clean
	rm -f $(PNG_FIGURES)

.PHONY: graphvizfigures
graphvizfigures: $(PNG_FIGURES) ## to generate images from dot files

%.dot.png: %.dot
	dot -Tpng $< -o $@

.PHONY: help
help: ## this help
	@echo "Please use \`make <target>' where <target> is one of"
	@awk 'BEGIN {FS = ":.*?## "} /^[a-zA-Z_-]+:.*?## / {printf "\033[36m%-30s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST) | sort

.DEFAULT_GOAL := help
