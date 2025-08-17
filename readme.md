# quick-meetings

This branch contains a bug with daylight savings while creating meetings.

## Run the failing test

To run the problematic test:

```bash
mvn clean test -Dgroups=test-being-demoed
```

## Bug: Start time is before end time

Although the meeting creation specifies the start-time at **02:34** and end-time at **03:04**, the
error thrown says the start time is after the end time.

```
Original Sample
---------------
  meetingArgs:
    MeetingArgs[fromDate=2025-03-30, fromTime=02:34:31, toDate=2025-03-30, toTime=03:04:31, timezone=Europe/Amsterdam]

  Original Error
  --------------
  java.lang.AssertionError:
    Expecting actual:
      "{"message":"Meeting cannot start (2025-03-30T03:34:31+02:00[Europe/Amsterdam]) after its end time (2025-03-30T03:04:31+02:00[Europe/Amsterdam])"}"
    to contain at least one of the following elements:
      ["Meeting created"]
    but none were found
```

This happens when the start or end time falls in a local timezone which is a "gap" due to daylight
savings. On 30 March 2025, at `02:00:00` clocks in France were turned forward 1 hour to `03:00:00` -
the time between 2 and 3 does not exist and is a gap.

### Fix

To fix this bug in the code, use strict zoned date time conversion(`./fix.sh`):

```
git revert --no-commit 20ac61b && git reset HEAD
```

## Switching Between Branches

There are some scripts for easier switching between branches / running tests:

| Script            | Branch                                                                                                               | Testing Area                                                                   |
|-------------------|----------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------|
| `demo-1.sh`     | [demo-1-server-never-returns-5xx](https://github.com/mourjo/quick-meetings/tree/demo-1-server-never-returns-5xx)     | Presentation: APIs should always return JSON                                   |
| `demo-2.sh`     | [demo-2-invalid-date-range](https://github.com/mourjo/quick-meetings/tree/demo-2-invalid-date-range)                 | Presentation: Valid date ranges should be accepted                             |
| `demo-3.sh`     | [demo-3-meeting-creation-scenarios](https://github.com/mourjo/quick-meetings/tree/demo-3-meeting-creation-scenarios) | A meeting cannot be created if it overlaps with an existing meeting            |
| `demo-4.sh`     | [demo-4-meeting-acceptations](https://github.com/mourjo/quick-meetings/tree/demo-4-meeting-acceptations)             | Interleaving multi-user actions should not allow overlapping meetings to exist |
| `demo-5.sh`     | [demo-5-empty-meetings](https://github.com/mourjo/quick-meetings/tree/demo-5-empty-meetings)                         | No end-user action can cause a meeting to become empty with no attendees       |
| `demo-reset.sh` | [main](https://github.com/mourjo/quick-meetings/)                                                                    | No failing test - All fixes implemented                                        |
| `fix*.sh`       |                                                                                                                      | Scripts that fixes bugs in the individual branches                             |
