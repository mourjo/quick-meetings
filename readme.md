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

```
Invariant failed after the following actions: [
    Inputs{action=CREATE, user=alice, from=2025-06-09T10:21Z, to=2025-06-09T10:22Z}
    Inputs{action=INVITE, user=bob, meetingIdx=0}
    Inputs{action=CREATE, user=bob,   from=2025-06-09T10:21Z, to=2025-06-09T10:22Z}
    Inputs{action=ACCEPT, user=bob, meetingIdx=0}  
]
```

The above output highlights how the bug happens:

- Alice creates `meeting 0`
- Bob gets invited to Alice's `meeting 0`
- Bob creates a meeting 1 that overlaps with Alice's `meeting 0` (so far, it is okay since Bob has
  not
  confirmed that he will attend Alice's `meeting 0`)
- Bob accepts the invitation to Alice's `meeting 0` (now this is a problem because the system
  allowed
  Bob to be in two meetings at the same time - Alice's `meeting 0` and Bob's own `meeting 1`)

But there are other more nuanced cases, which involves three users like the following. That is, the
same invariant fails for **different input conditions** but are caught by the same testing code:

```
Invariant failed after the following actions: [
    Inputs{action=CREATE, user=alice, from=2025-06-09T10:21Z, to=2025-06-09T10:22Z}
    Inputs{action=CREATE, user=bob,   from=2025-06-09T10:21Z, to=2025-06-09T10:22Z}
    Inputs{action=INVITE, user=charlie, meetingIdx=1}
    Inputs{action=INVITE, user=charlie, meetingIdx=0}
    Inputs{action=ACCEPT, user=charlie, meetingIdx=1}
    Inputs{action=ACCEPT, user=charlie, meetingIdx=0}  
]
```

The above output highlights how the bug happens:

- Alice creates a `meeting 0`
- Bob creates `meeting 1` at the same time as Alice's meeting 0 (but this is okay since the people
  attending are different, ie Alice and Bob)
- Charlie gets invited to Bob's `meeting 1`
- Charlie gets invited to Alice's `meeting 0`
- Charlie accepts invitation to Bob's `meeting 1`
- Charlie accepts invitation to Alice's `meeting 1` (this is a problem case because now Bob has
  confirmed that he will attend two meetings at the same time)

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
