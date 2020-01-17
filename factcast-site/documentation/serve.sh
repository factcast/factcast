#!/bin/bash

(
docker run --rm -u `id -u` -v $PWD:/srv/hugo yanqd0/hugo hugo
cd public
python -m SimpleHTTPServer 8080
)

