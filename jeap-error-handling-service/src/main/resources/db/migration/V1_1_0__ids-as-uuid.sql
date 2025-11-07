ALTER TABLE error
    DROP CONSTRAINT error_causing_event_ref;
ALTER TABLE scheduled_resend
    DROP CONSTRAINT scheduled_resend_error_id_fkey;

ALTER TABLE error
    alter column id type uuid using id::uuid;
ALTER TABLE error
    alter column causing_event_id type uuid using causing_event_id::uuid;
ALTER TABLE error
    alter column manual_task_id type uuid using manual_task_id::uuid;

ALTER TABLE causing_event
    alter column id type uuid using id::uuid;

ALTER TABLE scheduled_resend
    alter column id type uuid using id::uuid;
ALTER TABLE scheduled_resend
    alter column error_id type uuid using error_id::uuid;

ALTER TABLE error
    ADD CONSTRAINT error_causing_event_ref
        FOREIGN KEY (causing_event_id) references causing_event (id);
ALTER TABLE scheduled_resend
    ADD CONSTRAINT scheduled_resend_error_id_fkey
        FOREIGN KEY (error_id) references error (id);
