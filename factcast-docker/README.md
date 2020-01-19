# factcast-docker

This project builds a docker container factcast/factcast that can be used to
deploy a factcast server to a target environment.

Volumes that can be mounted:

/log (you should provide a logback.xml that uses this directory)

/config (optionally provide application.properties etc)

Usage:

```bash
docker run -v $PWD:/config -v $PWD/log:/log factcast/factcast
```
