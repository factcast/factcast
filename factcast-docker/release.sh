#!/bin/bash
../mvnw -Ddocker deploy docker:tag docker:push
echo "now you can do: "
echo ""
echo "docker tag factcast/factcast:latest factcast/factcast:<VERSION>"
echo "docker push factcast/factcast:<VERSION>"
echo ""

