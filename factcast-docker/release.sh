#!/bin/bash
mvn -Ddocker deploy dockerfile:tag dockerfile:push
