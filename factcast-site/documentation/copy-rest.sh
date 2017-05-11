#!/bin/bash
cp content/usage/rest/api-guide.header content/usage/rest/api-guide.adoc
cat ../../factcast-server-rest/src/docs/api-guide.adoc >> content/usage/rest/api-guide.adoc 
cp -r ../../factcast-server-rest/src/docs/generated-snippets .
cp -r ../../factcast-server-rest/src/docs/snippets .
