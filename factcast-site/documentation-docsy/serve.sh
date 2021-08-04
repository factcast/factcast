#!/bin/bash

#(
#docker run --rm -u `id -u`:`id -g` -p 1313:1313 -v $PWD:/srv/hugo peaceiris/hugo hugo server -w --bind 0.0.0.0
#)


hugo serve
