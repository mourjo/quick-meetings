# quick-meetings

This is a web application to create meetings. It uses property based testing to find obscure bugs.
Bugs that mere mortals like me would surely ship to production and break the system's invariants.

## Bugs caught by Property Based Testing

Check out each of the following branches with the bugs not fixed and see how property-based tests
catch them - the readme file in each of these branches explain how to run and fix the bugs -- from
simpler to complex:

1. [demo-1-server-never-returns-5xx](https://github.com/mourjo/quick-meetings/tree/demo-1-server-never-returns-5xx) -
   Does the API server always return valid JSON?
2. [demo-2-invalid-date-range](https://github.com/mourjo/quick-meetings/tree/demo-2-invalid-date-range) -
   Does the API server accept dates in the correct format?
3. [demo-3-meeting-creation-scenarios](https://github.com/mourjo/quick-meetings/tree/demo-3-meeting-creation-scenarios) -
   Can a meeting be created if it overlaps with the user's other meetings?
4. [demo-4-meeting-acceptations](https://github.com/mourjo/quick-meetings/tree/demo-4-meeting-acceptations) -
   Does any action break the invariant that no person can be in two meetings at the same time?
5. [demo-5-empty-meetings](https://github.com/mourjo/quick-meetings/tree/demo-5-empty-meetings) -
   Does any action create meetings with no attendees?

## Start the database with Docker

The docker compose file will create and initialize the database schema.

```bash
docker compose up
```

## Running the system

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
