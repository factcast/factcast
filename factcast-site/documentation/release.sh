#!/bin/bash
set -e
rm -rf stage
sudo rm -rf public

docker run --rm -v $PWD:/srv/hugo yanqd0/hugo hugo

mkdir stage -p
cp -r public/* stage
cp favicon.png stage/images/
rsync --delete -rcv stage/* con:/www/docs.factcast.org
rm -rf stage



