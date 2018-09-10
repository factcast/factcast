#!/bin/bash
set -e
rm -rf stage
rm -rf public

./hugo

mkdir stage -p
mv public/* stage
cp favicon.png stage/images/
rsync --delete -rcv stage/* wv:/www/docs.factcast.org
rm -rf stage



