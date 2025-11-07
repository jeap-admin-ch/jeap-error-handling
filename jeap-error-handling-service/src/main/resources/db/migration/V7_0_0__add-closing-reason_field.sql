ALTER TABLE error
    ADD closing_reason varchar(1000);

CREATE INDEX error_closing_reason ON error (closing_reason);
