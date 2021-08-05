FROM node:16-slim

RUN apt update && \
    apt install -y git && \
    mkdir /srv/hugo

WORKDIR /srv/hugo

RUN npm install -g autoprefixer postcss-cli hugo-extended@0.87.0

VOLUME /srv/hugo

CMD ["/bin/sh", "-c", "hugo server --bind 0.0.0.0 --navigateToChanged --templateMetrics --buildDrafts"]