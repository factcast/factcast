FROM node:19-slim

RUN apt update && \
    apt install -y git && \
    mkdir /srv/hugo


RUN npm install -g postcss-cli@9.1.0 hugo-extended@0.87.0

RUN cd /srv && npm install autoprefixer@10.4.7

WORKDIR /srv/hugo
VOLUME /srv/hugo

CMD ["/bin/sh", "-c", "hugo server --bind 0.0.0.0 --navigateToChanged --templateMetrics --buildDrafts"]