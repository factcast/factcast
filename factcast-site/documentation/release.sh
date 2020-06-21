#!/bin/bash

docker run --rm -u `id -u`:`id -g` -p 1313:1313 -v $PWD:/srv/hugo yanqd0/hugo:0.61.0 hugo

cp favicon.png public/images/

# example schema registry
mkdir -p static
cp -r ../../factcast-schema-registry-cli/output/public/ static/example-registry

rsync --delete -rcv public/* con:/www/docs.factcast.org
