#!/usr/bin/env bash


current_branch=$(git rev-parse --abbrev-ref HEAD) ;

if [ "$current_branch" = "demo-3-meeting-creation-scenarios" ]; then
  git revert --no-commit 7cb6fc948a49281e73dedfdf2899b3a04d8f34a9 && git reset HEAD
  mvn clean
else
  echo "Current branch is not demo-3-meeting-creation-scenarios"
fi


