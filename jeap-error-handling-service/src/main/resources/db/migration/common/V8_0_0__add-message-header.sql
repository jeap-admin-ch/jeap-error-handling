CREATE SEQUENCE message_header_seq;

CREATE TABLE message_header
(
    id               bigint PRIMARY KEY,
    causing_event_id uuid,
    header_name      varchar NOT NULL,
    header_value     bytea   NOT NULL
);

ALTER TABLE message_header
    ADD CONSTRAINT message_header_causing_event_ref
        FOREIGN KEY (causing_event_id) references causing_event (id);
