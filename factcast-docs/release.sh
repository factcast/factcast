#!/bin/bash
set -euo pipefail

cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
mvn-lite -B -Dantora.playbook=antora-playbook-release.yml clean package

# overwrite landing page
cp src/main/resources/index.html target/site/index.html
cp src/main/resources/index.html target/site/landing.html

rsync --delete -rcv target/site/* con2:/www/docs.factcast.org
