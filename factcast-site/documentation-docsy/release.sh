#!/bin/bash

# example schema registry
mkdir -p static
rm -rf static/example-registry
cp -r ../../factcast-schema-registry-cli/output/public/ static/example-registry

(
docker run --rm -u `id -u`:`id -g` -v $PWD/../..:/srv/hugo factcast/factcast-hugo hugo -s factcast-site/documentation-docsy --minify --templateMetrics --buildDrafts
)


rsync --delete -rcv public/* con:/www/docs.factcast.org
