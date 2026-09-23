#!/bin/bash

mvn clean install

rsync --delete -rcv public/* con2:/www/docs2.factcast.org
