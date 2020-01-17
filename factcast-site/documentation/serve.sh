#!/bin/bash

int_trap() {
 echo ""
}


trap int_trap INT

rm -rf stage
rm -rf public
rm -rf themes

#(
# wget -qO- https://github.com/uweschaefer/hugo-theme-docdock/archive/master.zip|bsdtar -xvf- 
#)

(
docker run --rm -u `id -u` -v $PWD:/srv/hugo yanqd0/hugo hugo
cd public
python -m SimpleHTTPServer 8080
)

