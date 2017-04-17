#!/bin/bash
set -e
rm -rf public
hugo -t hugo-elate-theme-master
mkdir stage/stealth -p
mv public/* stage/stealth
cat stealth.html >stage/index.html
rsync --delete -rcv stage/* wv:/www/www.factcast.org
rm -rf stealth

