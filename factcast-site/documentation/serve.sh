#!/bin/bash

(
docker run --rm -u `id -u` -p 1313:1313 -v $PWD:/srv/hugo yanqd0/hugo hugo server -w --bind 0.0.0.0
)

