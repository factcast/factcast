#!/bin/bash
../mvnw -Ddocker deploy

docker buildx rm fcbuild || true
docker buildx create --driver docker-container --name fcbuild --platform linux/amd64,linux/arm64

export fcversion=`../mvnw org.apache.maven.plugins:maven-help-plugin:2.1.1:evaluate -Dexpression=project.version --file ../pom.xml |grep -v "\[.*"` 
echo "Pushing version $fcversion"
docker buildx build \
  --progress=plain \
  --builder fcbuild \
  --platform linux/amd64,linux/arm64 \
  --tag docker.io/factcast/factcast:latest \
  --tag docker.io/factcast/factcast:$fcversion \
  --push \
  --file=./target/docker/factcast/factcast/tmp/docker-build/Dockerfile \
  ./target/docker/factcast/factcast/tmp/docker-build

