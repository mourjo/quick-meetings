\c postgres;

DROP DATABASE IF EXISTS quick_meetings_test_db;
CREATE DATABASE quick_meetings_test_db;
GRANT ALL PRIVILEGES ON DATABASE quick_meetings_test_db TO justin;

\c quick_meetings;


CREATE TABLE meetings (
   id serial PRIMARY KEY,
   name TEXT,
   from_ts TIMESTAMP WITH TIME ZONE NOT NULL,
   to_ts TIMESTAMP WITH TIME ZONE NOT NULL,
   created_ts TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
   updated_ts TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);


CREATE table users(
id serial PRIMARY KEY,
   name TEXT
);

CREATE table user_meetings(
id serial PRIMARY KEY,
meeting_id int not null references meetings(id) ON DELETE CASCADE,
user_id int not null references users(id) ON DELETE CASCADE,
role_of_user text not null,
updated_ts TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);


\c quick_meetings_test_db;


