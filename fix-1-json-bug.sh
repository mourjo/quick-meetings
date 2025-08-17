#!/usr/bin/env bash


current_branch=$(git rev-parse --abbrev-ref HEAD) ;

if [ "$current_branch" = "demo-1-server-never-returns-5xx" ]; then
  git revert --no-commit 69dae75b394a10e5bc45b75e22a1c4f3c287eb48 && git reset HEAD
  mvn clean
else
  echo "Current branch is not demo-1-server-never-returns-5xx"
fi
