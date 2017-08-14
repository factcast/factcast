var fs = require('fs');
var pkg = require('./package.json');

function buildHarHeaders (headers) {
  return headers ? Object.keys(headers).map(function (key) {
    return {
      name: key,
      value: headers[key]
    };
  }) : [];
}

function buildPostData (body) {
  return body ? {
    mimeType: 'application/json',
    text: body
  } : null;
}

function HarWrapper (requestModule) {
  this.requestModule = requestModule;
  this.clear();
}

HarWrapper.prototype.request = function (options) {
  Object.assign(options, { time: true });
  var self = this;
  return this.requestModule(options, function (err, incomingMessage, response) {
    if (err) return;
    self.entries.push(self.buildHarEntry(incomingMessage));
  });
};

HarWrapper.prototype.clear = function () {
  this.entries = [];
  this.earliestTime = new Date(2099, 1, 1);
};

HarWrapper.prototype.saveHar = function (fileName) {
  var httpArchive = {
    log: {
      version: '1.2',
      creator: {name: 'request-capture-har', version: pkg.version},
      pages: [{
        startedDateTime: new Date(this.earliestTime).toISOString(),
        id: 'request-capture-har',
        title: 'request-capture-har',
        pageTimings: { }
      }],
      entries: this.entries
    }
  };
  fs.writeFileSync(fileName, JSON.stringify(httpArchive, null, 2));
};

HarWrapper.prototype.buildHarEntry = function (response) {
  var startTimestamp = response.request.startTime;
  var responseStartTimestamp = response.request.response.responseStartTime;
  var endTimestamp = startTimestamp + response.elapsedTime;

  var waitingTime = responseStartTimestamp - startTimestamp;
  var totalTime = endTimestamp - startTimestamp;
  var receiveTime = endTimestamp - responseStartTimestamp;

  this.earliestTime = Math.min(new Date(startTimestamp), this.earliestTime);

  var entry = {
    startedDateTime: new Date(startTimestamp).toISOString(),
    time: totalTime,
    request: {
      method: response.request.method,
      url: response.request.uri.href,
      httpVersion: 'HTTP/' + response.httpVersion,
      cookies: [],
      headers: buildHarHeaders(response.request.headers),
      queryString: [],
      postData: buildPostData(response.request.body),
      headersSize: -1,
      bodySize: -1
    },
    response: {
      status: response.statusCode,
      statusText: response.statusMessage,
      httpVersion: 'HTTP/' + response.httpVersion,
      cookies: [],
      headers: buildHarHeaders(response.headers),
      _transferSize: response.body.length,
      content: {
        size: response.body.length,
        mimeType: response.headers['content-type']
      },
      redirectURL: '',
      headersSize: -1,
      bodySize: -1
    },
    cache: {},
    timings: {
      send: -1,
      wait: waitingTime,
      receive: receiveTime
    }
  };
  return entry;
};

module.exports = HarWrapper;
