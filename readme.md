# quick-meetings

This branch breaks the expectation that the server will always return JSON data and never 5xx statuses.

## Run the failing test

To run the problematic test:

```bash
mvn clean test -Dgroups=test-being-demoed
```

You can use this [Github Action](https://github.com/mourjo/quick-meetings/actions/workflows/test-being-demoed.yml) 
to run the test and see the output - make sure to use the correct branch `demo-1-server-never-returns-5xx`.

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

The error that causes it to fail is due to the Json parser not being able to understand HTML payloads:

```
  Original Error
  --------------
  com.fasterxml.jackson.core.JsonParseException:
    Unexpected character ('<' (code 60)): expected a valid value (JSON String, Number, Array, Object or token 'null', 'true' or 'false')
     at [Source: (String)"<html><body><h1>Whitelabel Error Page</h1><p>This application has no explicit mapping for /error, so you are seeing this as a fallback.</p><div id='created'>Sat May 24 14:11:11 CEST 2025</div><div>There was an unexpected error (type=Method Not Allowed, status=405).</div></body></html>"; line: 1, column: 1]
```

### Fix 1

To fix this, we need to tell Spring to ignore the request's accept header:

```
git revert --no-commit 69dae75 && git reset HEAD
```

## Bug 2: POST meeting cannot be done without a duration

If the `POST /meeting` endpoint is called without a meeting duration, the application throws a `NullPointerException`:

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

```
git revert --no-commit 575d8d3 && git reset HEAD
```
