# quick-meetings

Note: The database schema is read to generate Java classes. Start the Docker services before
compilation.

## Database Access

The database init script
is [here](https://github.com/mourjo/quick-meetings/blob/main/src/test/resources/init.sql). Connect
to the database using:

```bash
docker exec -it postgres_quick_meetings  psql -U justin -d quick_meetings_test_db
```
