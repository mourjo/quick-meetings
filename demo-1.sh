#!/usr/bin/env bash

git stash
git checkout demo-1-server-never-returns-5xx
mvn clean
