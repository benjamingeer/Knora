FROM adoptopenjdk/openjdk11:alpine-jre

RUN apk update && apk upgrade && apk add bash curl

COPY stage /upgrade

WORKDIR /upgrade/graphdb-se

ENTRYPOINT ./auto-upgrade.sh

LABEL MAINTAINER="400790+subotic@users.noreply.github.com"
