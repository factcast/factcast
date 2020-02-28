#!/bin/bash

cp favicon.png public/images/

# example schema registry
mkdir -p static
cp -r ../../factcast-schema-registry-cli/target/output/public/ static/example-registry

rsync --delete -rcv public/* con:/www/docs.factcast.org
