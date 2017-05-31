+++
draft = false
title = "JavaScript REST Consumer"
description = ""
date = "2017-04-24T18:36:24+02:00"

creatordisplayname = "Uwe Schaefer"
creatoremail = "uwe.schaefer@mercateo.com"

hidden = true

[menu.main]
parent = "usage"
identifier = "js_rest_consumer"
weight = 130


+++

## JavaScript REST Consumer

```html
<html>
<body>
  <div id="events"></div>
  <script>
    const params = new URLSearchParams();
    params.set('factSpec', JSON.stringify({
      ns: 'myapp'
    }));
    params.set('continuous', true);
    const subscription = new EventSource('http://localhost:8080/facts?' + params.toString());

    const div = document.getElementById('events');
    subscription.addEventListener('new-fact', (message) => {
      const fact = JSON.parse(message.data);
      const p = document.createElement('p');
      const text = document.createTextNode('Id: ' + message.lastEventId + ' ' + JSON.stringify(fact.payload));
      p.appendChild(text);
      div.appendChild(p)
    });
    </script>
</body>
</html>

```
