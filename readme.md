# quick-meetings

One of the possible interactions (creating/inviting/accepting meetings) breaks the
invariant that all meetings should have one confirmed attendee.

While this seems like a gross miss, when we work on individual features, it is difficult
to keep track of the larger product's vision or expectations. This branch is the same -
once we allow people to reject meeting invites, this bug manifests itself.

Although the bug is not in the rejection action alone, it breaks an expection: all
meetings should have one confirmed attendee.

## Run the failing test

To run the problematic test:

```bash
mvn clean test -Dgroups=test-being-demoed
```

## Bug: Accepting a meeting breaks invariant

The minimal set of operations that fail is simple - this is provided by the test output as well:

- Alice creates a meeting
- Alice rejects that same meeting

```
OperationsGenTests.noOperationCausesEmptyMeetings:70 Invariant failed after the following actions: [
    Inputs{action=CREATE, user=alice, from=2025-06-09T10:21Z, to=2025-06-09T10:22Z}
    Inputs{action=REJECT, user=alice, meetingIdx=0}
]
```

### Fix

Not allowing the owner to reject their own meeting fixes the problem (`./fix.sh`):

```
git revert --no-commit 6910be3 && git reset HEAD
```

Arguably, there should be a delete endpoint that does this rejection for the owner. And that
underscores the power of property based tests - no action should work in a way that breaks the
product's
operating principles.

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
