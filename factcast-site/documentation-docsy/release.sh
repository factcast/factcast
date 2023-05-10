#!/bin/bash

# example schema registry
mkdir -p static
rm -rf static/example-registry
cp -r ../../factcast-schema-registry-cli/output/public/ static/example-registry

(
docker run --rm -it \
  -v $PWD/../..:/src \
  -p 1313:1313 \
  klakegg/hugo:0.101.0-ext-debian -s factcast-site/documentation-docsy --minify --templateMetrics --buildDrafts
)


rsync --delete -rcv public/* con:/www/docs.factcast.org
