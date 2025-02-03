CREATE TABLE error_group
(
    id                      uuid                        PRIMARY KEY,
    error_code              varchar                     NOT NULL,
    event_name              varchar                     NOT NULL,
    error_publisher         varchar                     NOT NULL,
    error_message           varchar                     NOT NULL,
    error_stack_trace_hash  varchar,
    ticket_number           varchar,
    free_text               varchar,
    created                 timestamp with time zone    NOT NULL,
    modified                timestamp with time zone
);

CREATE UNIQUE INDEX error_group_uniquely_identifiable ON error_group (error_publisher, event_name, error_code, error_stack_trace_hash);

ALTER TABLE error
    ADD error_group_id uuid;

CREATE INDEX error_error_group_id ON error (error_group_id);

ALTER TABLE error
    ADD error_event_data_stack_trace_hash varchar;

ALTER TABLE error
    ADD CONSTRAINT error_group_id_fk
        FOREIGN KEY (error_group_id) references error_group (id);