CREATE TABLE causing_event
(
    id                         uuid PRIMARY KEY,
    message_payload            bytea                    NOT NULL,
    message_key                bytea,
    message_topic              varchar                  NOT NULL,
    message_partition          integer                  NOT NULL,
    message_offset             integer                  NOT NULL,
    metadata_id                varchar                  NOT NULL,
    metadata_idempotence_id    varchar                  NOT NULL,
    metadata_type_name         varchar                  NOT NULL,
    metadata_type_version      varchar                  NOT NULL,
    metadata_publisher_service varchar                  NOT NULL,
    metadata_publisher_system  varchar                  NOT NULL,
    metadata_created           timestamp with time zone NOT NULL
);

CREATE TABLE error
(
    id                                     uuid PRIMARY KEY,
    state                                  varchar                  NOT NULL,
    error_event_data_code                  varchar                  NOT NULL,
    error_event_data_temporality           varchar                  NOT NULL,
    error_event_data_message               varchar,
    error_event_data_description           varchar,
    error_event_data_stack_trace           varchar,
    error_event_metadata_id                varchar                  NOT NULL,
    error_event_metadata_idempotence_id    varchar                  NOT NULL,
    error_event_metadata_type_name         varchar                  NOT NULL,
    error_event_metadata_type_version      varchar                  NOT NULL,
    error_event_metadata_publisher_service varchar                  NOT NULL,
    error_event_metadata_publisher_system  varchar                  NOT NULL,
    error_event_metadata_created           timestamp with time zone NOT NULL,
    causing_event_id                       uuid
        constraint error_causing_event_ref references causing_event,
    created                                timestamp with time zone NOT NULL,
    modified                               timestamp with time zone,
    manual_task_id                         uuid,
    version                                integer
);

CREATE TABLE scheduled_resend
(
    id        uuid PRIMARY KEY,
    error_id  uuid references error (id),
    resend_at timestamp with time zone,
    resent_at timestamp with time zone,
    cancelled boolean,
    version   integer
);

CREATE TABLE shedlock
(
    name       VARCHAR(64),
    lock_until TIMESTAMP(3) NULL,
    locked_at  TIMESTAMP(3) NULL,
    locked_by  VARCHAR(255),
    PRIMARY KEY (name)
);

CREATE INDEX error_state ON error (state);
CREATE INDEX error_created ON error (created);
CREATE INDEX error_modified ON error (modified);
CREATE INDEX error_event_data_temporality ON error (error_event_data_temporality);
CREATE INDEX error_event_metadata_publisher_system ON error (error_event_metadata_publisher_system);
CREATE INDEX error_event_metadata_publisher_service ON error (error_event_metadata_publisher_service);
CREATE INDEX error_error_event_metadata_id ON error (error_event_metadata_id);
CREATE INDEX error_error_event_metadata_idempotence_id ON error (error_event_metadata_idempotence_id);

CREATE INDEX causing_event_metadata_id ON causing_event (metadata_id);
CREATE INDEX causing_event_metadata_idempotence_id ON causing_event (metadata_idempotence_id);
CREATE INDEX causing_event_metadata_created ON causing_event (metadata_created);
CREATE INDEX causing_event_metadata_type_name ON causing_event (metadata_type_name);
CREATE INDEX causing_event_metadata_publisher_system ON causing_event (metadata_publisher_system);
CREATE INDEX causing_event_metadata_publisher_service ON causing_event (metadata_publisher_service);

CREATE INDEX scheduled_resend_resend_at ON scheduled_resend (resend_at);
CREATE INDEX scheduled_resend_resent_at ON scheduled_resend (resent_at);
CREATE INDEX scheduled_resend_cancelled ON scheduled_resend (cancelled);
