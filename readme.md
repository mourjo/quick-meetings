# quick-meetings

This branch identifies a SQL bug which is easy to miss: identifying overlapping meetings.
Branch: `demo-3-meeting-creation-scenarios`

## Run the failing test

To run the problematic test:

```bash
mvn clean test -Dgroups=test-being-demoed
```

## Bug: SQL query to check overlaps is wrong

This demos the explore-and-shrink method in property based testing - this test fails when the second
meeting is overlapping with the first but only if the second meeting starts before and ends after
the first:

```
Shrunk Sample (130 steps)
-------------------------
  meeting1Start: 2025-01-01T10:00:01
  meeting1DurationMins: 1
  meeting2Start: 2025-01-01T10:00
  meeting2DurationMins: 2

Original Sample
---------------
  meeting1Start: 2025-01-01T20:09:29
  meeting1DurationMins: 6
  meeting2Start: 2025-01-01T19:55:33
  meeting2DurationMins: 49
```

## Fix

To fix this, use a different (arguably less intuitive) query:

```
git revert --no-commit 7b93eac && git reset HEAD
```