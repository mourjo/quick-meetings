#!/usr/bin/env bash

current_branch=$(git rev-parse --abbrev-ref HEAD) ;

if [ "$current_branch" = "demo-5-empty-meetings" ]; then
  git revert --no-commit 42db256 && git reset HEAD
  mvn clean
else
  echo "Current branch is not demo-5-empty-meetings"
fi
