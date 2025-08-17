#!/usr/bin/env bash


current_branch=$(git rev-parse --abbrev-ref HEAD) ;

if [ "$current_branch" = "demo-2-invalid-date-range" ]; then
  git revert --no-commit 20ac61bee2473dc1c663321e28ea7f61bf428f2b && git reset HEAD
  mvn clean
else
  echo "Current branch is not demo-2-invalid-date-range"
fi
