CREATE TABLE audit_log
(
    id                                     uuid PRIMARY KEY,
    user_auth_context                      varchar NOT NULL,
    user_subject                           varchar NOT NULL,
    user_ext_id                            varchar,
    user_given_name                        varchar,
    user_family_name                       varchar,
    error_id                               uuid constraint auditlog_error_ref references error,
    action                                 varchar,
    created                                timestamp with time zone NOT NULL
);

CREATE INDEX audit_log_error_id ON audit_log (error_id);

