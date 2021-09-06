#!/bin/bash
../mvnw -Ddocker deploy dockerfile:tag dockerfile:push
echo "now you can do: "
echo ""
echo "docker tag factcast/factcast:latest factcast/factcast:<VERSION>"
echo "docker push factcast/factcast:<VERSION>"
echo ""

