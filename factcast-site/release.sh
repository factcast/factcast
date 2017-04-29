#!/bin/bash
set -e
rm -rf stage
rm -rf front/public
rm -rf documentation/public

cd front
hugo --themesDir ~/hugo/themes
cd -

cd documentation
hugo --themesDir ~/hugo/themes
cd -

mkdir stage/stealth -p
mv front/public/* stage/stealth
mv documentation/public stage/documentation

cat stealth.html >stage/index.html
rsync --delete -rcv stage/* wv:/www/www.factcast.org
rm -rf stealth

