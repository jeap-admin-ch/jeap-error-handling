-- Note: Migration v5 is a Java Migration in V5_0_0__MigrateCausingEventIdsToBeUnique.java
ALTER TABLE causing_event
    ADD CONSTRAINT unique_causing_event_metadata_id UNIQUE (metadata_id);