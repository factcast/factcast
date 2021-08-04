FROM node:16-slim

#RUN apt update && \
#    apt install -y git && \
#    mkdir /srv/hugo

RUN apt update && \
    apt install -y git && \
    mkdir /srv/hugo

WORKDIR /srv/hugo

RUN npm install -g autoprefixer && \
    npm install -g postcss-cli && \
    npm install -g hugo-extended

VOLUME /srv/hugo

CMD ["/bin/sh", "-c", "hugo server --bind 0.0.0.0 --navigateToChanged --templateMetrics --buildDrafts"]