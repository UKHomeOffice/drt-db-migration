-- Create the database -- here it assumes the portCode of ema
/*
CREATE DATABASE ema;
\connect ema

-- Assumes user 'ema'

CREATE ROLE ema WITH LOGIN PASSWORD 'ema';
ALTER ROLE ema CREATEDB;

*/

-- SQL From https://github.com/dnvriend/akka-persistence-jdbc/blob/master/src/test/resources/schema/postgres/postgres-schema.sql
DROP TABLE IF EXISTS public.journal;

CREATE TABLE IF NOT EXISTS public.journal (
  ordering BIGSERIAL,
  persistence_id VARCHAR(255) NOT NULL,
  sequence_number BIGINT NOT NULL,
  deleted BOOLEAN DEFAULT FALSE,
  tags VARCHAR(255) DEFAULT NULL,
  message BYTEA NOT NULL,
  PRIMARY KEY(persistence_id, sequence_number)
);

CREATE UNIQUE INDEX journal_ordering_idx ON public.journal(ordering);

DROP TABLE IF EXISTS public.snapshot;

CREATE TABLE IF NOT EXISTS public.snapshot (
  persistence_id VARCHAR(255) NOT NULL,
  sequence_number BIGINT NOT NULL,
  created BIGINT NOT NULL,
  snapshot BYTEA NOT NULL,
  PRIMARY KEY(persistence_id, sequence_number)
);

-- Assumes user `ema` below - you may want to change it to the user you require
GRANT ALL PRIVILEGES ON DATABASE ema TO ema;
GRANT ALL PRIVILEGES ON TABLE journal TO ema;
GRANT ALL PRIVILEGES ON TABLE snapshot TO ema;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO ema;
