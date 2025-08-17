# quick-meetings

This is a web application to create meetings. It uses property based testing to find obscure bugs.
Bugs that mere mortals like me would surely ship to production and break the system's invariants.

The expectation is simple: We want to disallow any meeting that overlaps with an existing meeting --
ie, the system should not allow a person to be in two meetings at the same time. For example, the
meetings in red should not be allowed, while the green meetings are okay:
<img src="src/test/resources/overlap_cases.jpg" width="600">

More details
in [this blog post](https://mourjo.me/blog/tech/2025/05/25/quick-meetings-why-you-need-property-based-tests/).

## Bugs caught by Property Based Testing

Check out each of the following branches with the bugs not fixed and see how property-based tests
catch them.

The readme file in each of these branches explain how to run and fix the bugs -- from simpler to
complex:

- [Does the API server always return valid JSON?](https://github.com/mourjo/quick-meetings/tree/demo-1-server-never-returns-5xx)
- [Does the API server accept dates in the correct format?](https://github.com/mourjo/quick-meetings/tree/demo-2-invalid-date-range)
- [Can a meeting be created if it overlaps with the user's other meetings?](https://github.com/mourjo/quick-meetings/tree/demo-3-meeting-creation-scenarios)
- [Does any action allow a person to be in two meetings at the same time?](https://github.com/mourjo/quick-meetings/tree/demo-4-meeting-acceptations)
- [Can we end up with meetings with no attendees?](https://github.com/mourjo/quick-meetings/tree/demo-5-empty-meetings)

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

## Running the System

Initialize the database with the schema:

```bash
docker compose up
```

Start the server:

```bash
mvn spring-boot:run 
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
