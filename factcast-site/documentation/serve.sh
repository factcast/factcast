#!/bin/bash

int_trap() {
 echo ""
}


trap int_trap INT

set -e
rm -rf stage
rm -rf public
rm -r hugo-theme*
wget -qO- https://github.com/uweschaefer/hugo-theme-docdock/archive/master.zip|bsdtar -xvf- 

(
 hugo server --watch --bind 192.168.3.11
)

