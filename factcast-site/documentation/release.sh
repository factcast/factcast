#!/bin/bash
set -e
rm -rf stage
rm -rf public
rm -r hugo-theme*
wget -qO- https://github.com/uweschaefer/hugo-theme-docdock/archive/master.zip|bsdtar -xvf- 

hugo

mkdir stage -p
mv public/* stage
cp favicon.png stage/images/
#mv stage/index.html stage/_.html
#cat stealth.html >stage/index.html
rsync --delete -rcv stage/* wv:/www/docs.factcast.org
rm -rf stage



