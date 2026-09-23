#!/bin/bash

CI=true mvn clean install

rsync --delete -rcv target/site/* con2:/www/docs2.factcast.org
