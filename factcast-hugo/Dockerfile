FROM node:16-slim

ADD resources/hugo_extended_0.87.0_Linux-64bit.deb /opt/hugo_extended_0.87.0_Linux-64bit.deb

RUN dpkg -i /opt/hugo_extended_0.87.0_Linux-64bit.deb && mkdir /srv/hugo

WORKDIR /srv/hugo

RUN npm install -g autoprefixer && \
    npm install -g postcss-cli

VOLUME /srv/hugo

CMD ["/bin/sh", "-c", "hugo server --bind 0.0.0.0 --navigateToChanged --templateMetrics --buildDrafts"]