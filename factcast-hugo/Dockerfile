FROM node:19-slim

RUN apt update && \
    apt install -y git && \
    mkdir /srv/hugo


RUN npm install -g postcss-cli hugo-extended@0.87.0

RUN cd /srv && npm install autoprefixer

WORKDIR /srv/hugo
VOLUME /srv/hugo

CMD ["/bin/sh", "-c", "hugo server --bind 0.0.0.0 --navigateToChanged --templateMetrics --buildDrafts"]