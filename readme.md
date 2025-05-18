# quick-meetings

Branch: `demo-1-server-never-returns-5xx`

This branch breaks the expectation that the server will always return JSON and never 5xx.

### Run the failing test

To run the problematic test:

```bash
mvn clean test -Dgroups=test-being-demoed
```

### Bug 1: Non-JSON response body

If the accept header is `text/html`, Spring will try to return an HTML page, which is not what we
want from an API.

```
  Original Error
  --------------
  com.fasterxml.jackson.core.JsonParseException:
    Unexpected character ('<' (code 60)): expected a valid value (JSON String, Number, Array, Object or token 'null', 'true' or 'false')
     at [Source: (String)"<html><body><h1>Whitelabel Error Page</h1><p>This application has no explicit mapping for /error, so you are seeing this a
```

To fix this, tell Spring to ignore the request's accept header:
`git revert --no-commit 69dae75 && git reset HEAD`

### Bug 2: POST meeting cannot be done without a duration

If the `POST /meeting` endpoint is called without a meeting duration (first snippet below), there is
a `NullPointerException`:

```json
{
  "userId": 9007199254740991,
  "name": "string",
  "duration": {
    "from": {
      "date": "2025-05-18",
      "time": "14:30:00"
    },
    "to": {
      "date": "2025-05-18",
      "time": "14:30:00"
    }
  },
  "timezone": "string"
}
```

The error is:

```
 Cannot invoke "me.mourjo.quickmeetings.web.dto.MeetingDuration.from()" because the return value of "me.mourjo.quickmeetings.web.dto.MeetingCreationRequest.duration()" is null
```

To fix this, add global exception handlers that construct proper error messages
`git revert --no-commit 575d8d3 && git reset HEAD`