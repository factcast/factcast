#!/bin/bash

CI=true mvn clean install

# overwrite landing page
cp src/main/resources/index.html target/site/landing.html

rsync --delete -rcv target/site/* con2:/www/docs2.factcast.org
