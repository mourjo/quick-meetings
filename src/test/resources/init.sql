\c postgres;

DROP DATABASE IF EXISTS quick_meetings_test_db;
CREATE DATABASE quick_meetings_test_db;
GRANT ALL PRIVILEGES ON DATABASE quick_meetings_test_db TO justin;

\c quick_meetings_test_db;


CREATE TABLE meetings (
   id serial PRIMARY KEY,
   name TEXT,
   start_at TIMESTAMP WITH TIME ZONE NOT NULL,
   end_at TIMESTAMP WITH TIME ZONE NOT NULL,
   created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
   updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

