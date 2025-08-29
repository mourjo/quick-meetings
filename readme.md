# ⚡Quick Meetings ⚡

This is a web application for creating and managing meetings.
Unlike traditional testing, which relies on manually writing individual test cases, this project
uses property-based testing to uncover subtle bugs.

<img src="src/test/resources/bug.png" width="600">

Instead of checking predefined scenarios, we
rely on system properties (invariants) and let the test framework automatically generate diverse
input combinations to explore the problem space.

The fundamental expectation is simple: We want to disallow any meeting that overlaps with an
existing meeting -- ie, the system should not allow a person to be in two meetings at the same time.
For example, the meetings in red should not be allowed, while the green meetings are okay:

<img src="src/test/resources/overlap_cases.jpg" width="600">

## What could possibly go wrong?

This is a simple application with five endpoints. They are manually tested and works for the simple
cases.

<img src="src/test/resources/swagger.png" width="600">

However, each of the following branches highlight the bugs in different parts of the system that are
found by
property-based tests.

The `main` branch includes the fixes for the bugs. There are dedicated branches for the bugs that
were fixed because of property-based tests. The readme file in each of these branches explain how to
run and fix the
bugs -- from **simpler to complex**:

- [Does the API server always return valid JSON?](https://github.com/mourjo/quick-meetings/tree/demo-1-server-never-returns-5xx)
- [Does the API server accept dates in the correct format?](https://github.com/mourjo/quick-meetings/tree/demo-2-invalid-date-range)
- [Can a meeting be created if it overlaps with the user's other meetings?](https://github.com/mourjo/quick-meetings/tree/demo-3-meeting-creation-scenarios)
- [Does any action allow a person to be in two meetings at the same time?](https://github.com/mourjo/quick-meetings/tree/demo-4-meeting-acceptations)
- [Can we end up with meetings with no attendees?](https://github.com/mourjo/quick-meetings/tree/demo-5-empty-meetings)

## Switching Between Branches

There are some scripts for easier switching between branches / running tests:

| Script          | Branch                                                                                                               | Testing Area                                                                   |
|-----------------|----------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------|
| `demo-1.sh`     | [demo-1-server-never-returns-5xx](https://github.com/mourjo/quick-meetings/tree/demo-1-server-never-returns-5xx)     | Presentation: APIs should always return JSON                                   |
| `demo-2.sh`     | [demo-2-invalid-date-range](https://github.com/mourjo/quick-meetings/tree/demo-2-invalid-date-range)                 | Presentation: Valid date ranges should be accepted                             |
| `demo-3.sh`     | [demo-3-meeting-creation-scenarios](https://github.com/mourjo/quick-meetings/tree/demo-3-meeting-creation-scenarios) | A meeting cannot be created if it overlaps with an existing meeting            |
| `demo-4.sh`     | [demo-4-meeting-acceptations](https://github.com/mourjo/quick-meetings/tree/demo-4-meeting-acceptations)             | Interleaving multi-user actions should not allow overlapping meetings to exist |
| `demo-5.sh`     | [demo-5-empty-meetings](https://github.com/mourjo/quick-meetings/tree/demo-5-empty-meetings)                         | No end-user action can cause a meeting to become empty with no attendees       |
| `demo-reset.sh` | [main](https://github.com/mourjo/quick-meetings/)                                                                    | No failing test - All fixes implemented                                        |
| `fix*.sh`       |                                                                                                                      | Scripts that fixes bugs in the individual branches                             |

## Running the System

Initialize the database with the schema:

```bash
docker compose up
```

Start the server:

```bash
mvn spring-boot:run 
```

Alternatively, compile it into a Jar and then run the Jar:

```bash
mvn clean package spring-boot:repackage -DskipTests 
java -jar target/quickmeetings-0.0.1-SNAPSHOT.jar
```

This should start the local server
on [localhost:9981](http://localhost:9981/swagger-ui/index.html#/)

## Database Access

The database init script
is [here](https://github.com/mourjo/quick-meetings/blob/main/src/test/resources/init.sql). Connect
to the database using:

```bash
docker exec -it postgres_quick_meetings  psql -U justin -d quick_meetings_test_db
```
