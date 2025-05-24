# quick-meetings

This branch identifies a SQL bug which is easy to miss while identifying overlapping meetings.

## Run the failing test

To run the problematic test:

```bash
mvn clean test -Dgroups=test-being-demoed
```

## Bug: SQL query to check overlaps is wrong

This test fails when the second meeting is overlapping with the first but only if the 
second meeting **starts before** and **ends after** the first - note how the original
sample finds a larger time overlap but the shrunk example finds the smallest failing 
case:

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

## The query that was problematic
The following query has a bug (in the last AND clause) - it is quite hard to catch it at first glance:

```sql
SELECT *
FROM
meetings existing_meeting JOIN user_meetings um ON existing_meeting.id = um.meeting_id
WHERE um.user_id IN (:userIds)
AND um.role_of_user IN ('OWNER', 'ACCEPTED')
AND (
  (existing_meeting.from_ts <= $1 AND existing_meeting.to_ts >= $1)
  OR
  (existing_meeting.from_ts <= $2 AND existing_meeting.to_ts >= $2)
)
```

### Fix

To fix this, use a different (arguably less intuitive) query:

```
git revert --no-commit 7cb6fc9 && git reset HEAD
```
