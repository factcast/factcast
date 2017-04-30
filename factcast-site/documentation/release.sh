#!/bin/bash
set -e
rm -rf stage
rm -rf public
rm -rf themes
tar xzf themes.tgz

hugo

mkdir stage -p
mv public/* stage

mv stage/index.html stage/_.html
cat stealth.html >stage/index.html
rsync --delete -rcv stage/* wv:/www/docs.factcast.org
rm -rf stealth
rm -rf themes



