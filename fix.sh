#!/usr/bin/env bash


current_branch=$(git rev-parse --abbrev-ref HEAD) ;

if [ "$current_branch" = "demo-3-meeting-creation-scenarios" ]; then
  git restore --source eb949fb9a4c3957e31a6e03ac1b277203ab6be4f src/main/java/me/mourjo/quickmeetings/db/MeetingRepository.java
  mvn clean
else
  echo "Current branch is not demo-3-meeting-creation-scenarios"
fi


