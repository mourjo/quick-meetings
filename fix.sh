#!/usr/bin/env bash


current_branch=$(git rev-parse --abbrev-ref HEAD) ;

if [ "$current_branch" = "demo-2-invalid-date-range" ]; then
  git revert --no-commit 20ac61b && git reset HEAD
  mvn clean
else
  echo "Current branch is not demo-2-invalid-date-range"
fi
