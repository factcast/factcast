--liquibase createApplicationUser sql
--changeset akp:issue372

CREATE USER "factcast-application-user" WITH PASSWORD 'factcast-application-user';
GRANT SELECT, INSERT, DELETE, UPDATE ON TABLE fact, tokenstore, catchup TO "factcast-application-user";
GRANT EXECUTE ON  FUNCTION notifyFactInsert TO "factcast-application-user";
GRANT USAGE,SELECT ON SEQUENCE catchup_seq, fact_ser_seq TO "factcast-application-user";