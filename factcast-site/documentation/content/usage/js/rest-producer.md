+++
draft = false
title = "JavaScript REST Producer"
description = ""
date = "2017-04-24T18:36:24+02:00"

creatordisplayname = "Uwe Schaefer"
creatoremail = "uwe.schaefer@mercateo.com"

[menu.main]
parent = "usage"
identifier = "js_rest_producer"
weight = 120

+++

## JavaScript REST Producer

```html
<html>
<body>
  <div id="events"></div>
  <script>
   fetch('http://localhost:8080/transactions', {
     method: 'POST',
     headers: {
       'Content-Type': 'application/json'
     },
     body: JSON.stringify({
       facts: [
         {
           header: {
             ns: 'myapp',
             id: '1ef7e938-ee94-4989-ba9e-8bb8ddf9c03b'
           },
           payload: {
             foo: 'bar'
           }
         }
       ]
     })
   }).then(() => console.log('published'));
  </script>
</body>
</html>

```
