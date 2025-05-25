# quick-meetings

This is a web application to create meetings. It uses property based testing to find obscure bugs.
Bugs that mere mortals like me would surely ship to production and break the system's invariants.

More details in [this blog post](https://mourjo.me/blog/musings/2025/05/25/quick-meetings-why-you-need-property-based-tests/).

## Bugs caught by Property Based Testing

Check out each of the following branches with the bugs not fixed and see how property-based tests
catch them.

The readme file in each of these branches explain how to run and fix the bugs -- from simpler to complex:

- [Does the API server always return valid JSON?](https://github.com/mourjo/quick-meetings/tree/demo-1-server-never-returns-5xx)
- [Does the API server accept dates in the correct format?](https://github.com/mourjo/quick-meetings/tree/demo-2-invalid-date-range)
- [Can a meeting be created if it overlaps with the user's other meetings?](https://github.com/mourjo/quick-meetings/tree/demo-3-meeting-creation-scenarios)
- [Does any action allow a person to be in two meetings at the same time?](https://github.com/mourjo/quick-meetings/tree/demo-4-meeting-acceptations)
- [Can we end up with meetings with no attendees?](https://github.com/mourjo/quick-meetings/tree/demo-5-empty-meetings)
  

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
