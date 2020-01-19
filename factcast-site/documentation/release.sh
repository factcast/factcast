#!/bin/bash
cp favicon.png public/images/
rsync --delete -rcv public/* con:/www/docs.factcast.org
