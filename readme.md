# quick-meetings

This branch breaks the expectation that the server will always return JSON data and never 5xx
statuses.

## Run the failing test

To run the problematic test:

```bash
mvn clean test -Dgroups=test-being-demoed
```

## Bug 1: Non-JSON response body

If the accept header is `text/html`, Spring will try to return an HTML page, which is not what we
want from an API.

The following is a shrunk sample from the property based test:

```
Shrunk Sample (8 steps)
-----------------------
  method: "GET"
  path: "/user"
  contentTypeHeader: "text/html"
  acceptHeader: "text/html"
  body:
    "{
      "userId": 1,
      "name": "A",
      "duration": {
        "from": {
          "date": "2025-06-09",
          "time": "12:40"
        },
        "to": {
          "date": "2025-06-09",
          "time": "12:40"
        }
      },
      "timezone": "Asia/Kolkata"
    }
    "
```

The error that causes it to fail is due to the Json parser not being able to understand HTML
payloads:

```
  Original Error
  --------------
  com.fasterxml.jackson.core.JsonParseException:
    Unexpected character ('<' (code 60)): expected a valid value (JSON String, Number, Array, Object or token 'null', 'true' or 'false')
     at [Source: (String)"<html><body><h1>Whitelabel Error Page</h1><p>This application has no explicit mapping for /error, so you are seeing this as a fallback.</p><div id='created'>Sat May 24 14:11:11 CEST 2025</div><div>There was an unexpected error (type=Method Not Allowed, status=405).</div></body></html>"; line: 1, column: 1]
```

### Fix 1

To fix this, we need to tell Spring to ignore the request's accept header (`./fix-1-json-bug.sh`):

```
git revert --no-commit 69dae75 && git reset HEAD
```

## Bug 2: POST meeting cannot be done without a duration

If the `POST /meeting` endpoint is called without a meeting duration, the application throws a
`NullPointerException`:

```
Shrunk Sample (3 steps)
-----------------------
  method: "POST"
  path: "/meeting"
  contentTypeHeader: "application/json"
  acceptHeader: "text/html"
  body: "{   "meetingId": 1,   "invitees": [] } "
```

The error is:

```
 Cannot invoke "me.mourjo.quickmeetings.web.dto.MeetingDuration.from()" because the return value of "me.mourjo.quickmeetings.web.dto.MeetingCreationRequest.duration()" is null
```

And the user gets the following unhelpful response:

```
Status: 500, Body: {"timestamp":"2025-05-24T12:13:44.449+00:00","status":500,"error":"Internal Server Error","path":"/meeting"}
```

### Fix 2

To fix this, we need to add global exception handlers that construct proper error messages
(`./fix-2-exception-handling.sh`):

```
git revert --no-commit 575d8d3 && git reset HEAD
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
