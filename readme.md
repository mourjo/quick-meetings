# quick-meetings

This branch tests user interactions - creating meetings, inviting others to meetings and accepting
meeting invites.

One of these interactions breaks the invariant that no person can be in two meetings at the same
time.

## Run the failing test

To run the problematic test:

```bash
mvn clean test -Dgroups=test-being-demoed
```

## Bug: Accepting a meeting breaks invariant

Minimal subset of operations that fail (this varies between runs) is shown below

- Alice and Charlie create overlapping meetings - this is okay because our invariant is no person
  can be at the same meeting, but if the attendees are different (here Alice and Charlie), it is
  perfectly fine
- It is also not a problem when Charlie is invited to Alice's meeting
- It becomes a problem when Charlie accepts that meeting - because it overlaps with the meeting he
  created
- This is the minimal set of operations that shows the bug

```
OperationsGenTests.noOperationCausesAnOverlap:63 Invariant failed after the following actions: [
    Inputs{action=CREATE, user=alice, from=2025-06-09T10:21Z, to=2025-06-09T10:22Z}
    Inputs{action=INVITE, user=charlie, meetingIdx=0}
    Inputs{action=CREATE, user=charlie, from=2025-06-09T10:21Z, to=2025-06-09T10:22Z}
    Inputs{action=ACCEPT, user=charlie, meetingIdx=0}
]
```

But there are other more nuanced cases like this which sometimes get reported as well:

- Charlie creates a meeting 0
- Bob is invited to meeting 0
- Alice creates a meeting 1 (overlapping with meeting 0)
- Bob is invited to meeting 1
- Bob accepts the invitation to meeting 0 (still no overlap)
- Bob accepts the invitation to meeting 1 (this causes an overlap for Bob)

```
OperationsGenTests.noOperationCausesAnOverlap:67 Invariant failed after the following actions: [
    Inputs{action=CREATE, user=charlie, from=2025-06-09T10:21Z, to=2025-06-09T10:22Z}
    Inputs{action=INVITE, user=bob, meetingIdx=0}
    Inputs{action=CREATE, user=alice, from=2025-06-09T10:21Z, to=2025-06-09T10:22Z}
    Inputs{action=INVITE, user=bob, meetingIdx=0}
    Inputs{action=ACCEPT, user=bob, meetingIdx=0}
    Inputs{action=ACCEPT, user=bob, meetingIdx=1}
]
```

### Fix

Checking for an overlap before accepting a meeting solves the bug:

```
git revert --no-commit 6910be3 && git reset HEAD
```

## Switching Between Branches

There are some scripts for easier switching between branches / running tests:

| Script            | Branch                                                                                                               | Testing Area                                                                   |
|-------------------|----------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------|
| `./demo-1.sh`     | [demo-1-server-never-returns-5xx](https://github.com/mourjo/quick-meetings/tree/demo-1-server-never-returns-5xx)     | Presentation: APIs should always return JSON                                   |
| `./demo-2.sh`     | [demo-2-invalid-date-range](https://github.com/mourjo/quick-meetings/tree/demo-2-invalid-date-range)                 | Presentation: Valid date ranges should be accepted                             |
| `./demo-3.sh`     | [demo-3-meeting-creation-scenarios](https://github.com/mourjo/quick-meetings/tree/demo-3-meeting-creation-scenarios) | A meeting cannot be created if it overlaps with an existing meeting            |
| `./demo-4.sh`     | [demo-4-meeting-acceptations](https://github.com/mourjo/quick-meetings/tree/demo-4-meeting-acceptations)             | Interleaving multi-user actions should not allow overlapping meetings to exist |
| `./demo-5.sh`     | [demo-5-empty-meetings](https://github.com/mourjo/quick-meetings/tree/demo-5-empty-meetings)                         | No end-user action can cause a meeting to become empty with no attendees       |
| `./demo-reset.sh` | [main](https://github.com/mourjo/quick-meetings/)                                                                    | No failing test - All fixes implemented                                        |
| `./fix*.sh`       |                                                                                                                      | Scripts that fixes bugs in the individual branches                             |
