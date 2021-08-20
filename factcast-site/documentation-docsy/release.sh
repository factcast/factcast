#!/bin/bash

(
docker run --rm -u `id -u`:`id -g` -v $PWD/../..:/srv/hugo factcast/factcast-hugo hugo -s factcast-site/documentation-docsy --minify --templateMetrics --buildDrafts
)


# example schema registry
mkdir -p static
cp -r ../../factcast-schema-registry-cli/output/public/ static/example-registry

rsync --delete -rcv public/* con:/www/docs.factcast.org
