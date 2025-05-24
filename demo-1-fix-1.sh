#!/usr/bin/env bash


current_branch=$(git rev-parse --abbrev-ref HEAD) ;

if [ "$current_branch" = "demo-1-server-never-returns-5xx" ]; then
  git revert --no-commit 69dae75 && git reset HEAD
else
  echo "Current branch is not demo-1-server-never-returns-5xx"
fi
