#!/bin/bash

int_trap() {
 echo ""
}


trap int_trap INT

if [ ! -e themes ];
then
 tar xzf themes.tgz
fi
(
 hugo server --watch
)

