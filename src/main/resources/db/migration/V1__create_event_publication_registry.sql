-- Event publication registry required by Spring Modulith.
--
-- This is infrastructure, not domain: it is the table spring-modulith-starter-jpa uses to
-- record application events until every listener has completed. It is created here rather
-- than by the framework's own initializer because the schema belongs to Flyway, and
-- Hibernate runs with ddl-auto=validate — an entity mapped to a table no migration
-- describes fails the application at startup, which is the intended behaviour.
--
-- Column types mirror the mapping of org.springframework.modulith.events.jpa.JpaEventPublication.
-- No index is created yet: no event is published anywhere in the application, and an index
-- chosen before the access pattern exists is a guess.

CREATE TABLE event_publication (
    id                UUID        NOT NULL,
    listener_id       TEXT        NOT NULL,
    event_type        TEXT        NOT NULL,
    serialized_event  TEXT        NOT NULL,
    publication_date  TIMESTAMPTZ NOT NULL,
    completion_date   TIMESTAMPTZ,
    CONSTRAINT event_publication_pkey PRIMARY KEY (id)
);
