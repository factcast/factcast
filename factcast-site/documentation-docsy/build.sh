#!/bin/bash

(
docker run --rm -it \
  -v $PWD/../..:/src \
  -p 1313:1313 \
  klakegg/hugo:0.101.0-ext-debian -s factcast-site/documentation-docsy --minify --debug --log
)

