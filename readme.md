# quick-meetings

Branch: `demo-4-meeting-acceptations`

This branch tests user interactions - creating meetings, inviting others to meetings and accepting
meeting invites. One of these breaks the invariant that no person can be in two meetings at the same
time.

## Run the failing test

To run the problematic test:

```bash
mvn clean test -Dgroups=test-being-demoed
```

## Bug: Accepting a meeting breaks invariant

Minimal subset of operations that fail (this varies between runs) is shown below

- Alice and Bob create overlapping meetings - this is okay because our invariant is no person can be
  at the same meeting, but if the attendees are different (here Alice and Bob), it is perfectly fine
- It is also not a problem when Bob is invited to Alice's meeting
- It becomes a problem when Bob accepts that meeting - because it overlaps with the meeting he
  created

```
  After Execution
  ---------------
    ops:
      [
        Inputs{action=CREATE, user=alice, from=2025-06-09T10:21Z, to=2025-06-09T10:22Z, createdId=12461782},
        Inputs{action=INVITE, user=bob, meeting=12461782}, 
        Inputs{action=CREATE, user=bob, from=2025-06-09T10:21Z, to=2025-06-09T10:22Z, createdId=12461783},
        Inputs{action=ACCEPT, user=bob, meeting=12461782}
      ]
```

### Fix

```
git revert --no-commit 6910be3 && git reset HEAD
```

