ALTER TABLE causing_event ADD COLUMN origin VARCHAR DEFAULT 'KAFKA_MESSAGE';
UPDATE causing_event SET origin = 'KAFKA_MESSAGE' WHERE origin IS NULL;
ALTER TABLE causing_event ALTER COLUMN origin SET NOT NULL;

ALTER TABLE causing_event ALTER COLUMN message_payload DROP NOT NULL;
ALTER TABLE causing_event ALTER COLUMN message_topic DROP NOT NULL;
ALTER TABLE causing_event ALTER COLUMN message_partition DROP NOT NULL;
ALTER TABLE causing_event ALTER COLUMN message_offset DROP NOT NULL;

ALTER TABLE causing_event ADD COLUMN modulith_publication_id VARCHAR;
ALTER TABLE causing_event ADD COLUMN modulith_listener VARCHAR;
ALTER TABLE causing_event ADD COLUMN modulith_event_type VARCHAR;
ALTER TABLE causing_event ADD COLUMN modulith_serialized_event BYTEA;
ALTER TABLE causing_event ADD COLUMN modulith_serialized_event_content_type VARCHAR;
ALTER TABLE causing_event ADD COLUMN modulith_retry_command_topic VARCHAR;
ALTER TABLE causing_event ADD COLUMN modulith_discard_command_topic VARCHAR;

CREATE INDEX causing_event_origin ON causing_event (origin);
CREATE INDEX causing_event_modulith_publication_id ON causing_event (modulith_publication_id);

CREATE SEQUENCE deferred_message_sequence START WITH 1 INCREMENT BY 1;

CREATE TABLE deferred_message
(
    id                     BIGINT PRIMARY KEY,
    message                BYTEA                    NOT NULL,
    "key"                  BYTEA,
    cluster_name           VARCHAR,
    topic                  VARCHAR                  NOT NULL,
    message_id             VARCHAR                  NOT NULL,
    message_idempotence_id VARCHAR                  NOT NULL,
    message_type_name      VARCHAR                  NOT NULL,
    message_type_version   VARCHAR,
    created                TIMESTAMP WITH TIME ZONE NOT NULL,
    send_immediately       BOOLEAN,
    schedule_after         TIMESTAMP WITH TIME ZONE,
    sent_immediately       TIMESTAMP WITH TIME ZONE,
    sent_scheduled         TIMESTAMP WITH TIME ZONE,
    failed                 TIMESTAMP WITH TIME ZONE,
    fail_reason            VARCHAR,
    resend                 BOOLEAN DEFAULT FALSE,
    trace_id_high          BIGINT,
    trace_id               BIGINT,
    span_id                BIGINT,
    parent_span_id         BIGINT,
    trace_id_string        VARCHAR,
    sampled                BOOLEAN
);

CREATE INDEX deferred_message_created ON deferred_message (created);
CREATE INDEX deferred_message_send_immediately ON deferred_message (send_immediately);
CREATE INDEX deferred_message_schedule_after ON deferred_message (schedule_after);
CREATE INDEX deferred_message_sent_immediately ON deferred_message (sent_immediately);
CREATE INDEX deferred_message_sent_scheduled ON deferred_message (sent_scheduled);
CREATE INDEX deferred_message_failed ON deferred_message (failed);
CREATE INDEX deferred_message_resend ON deferred_message (resend);
