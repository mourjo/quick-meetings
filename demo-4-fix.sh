#!/bin/bash

current_branch=$(git rev-parse --abbrev-ref HEAD) ;

if [ "$current_branch" = "demo-4-meeting-acceptations" ]; then
  git revert --no-commit 6910be3 && git reset HEAD
else
  echo "Current branch is not demo-4-meeting-acceptations"
fi
