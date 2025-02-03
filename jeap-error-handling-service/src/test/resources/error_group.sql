INSERT INTO error_group (id, error_code, event_name, error_publisher, error_message, error_stack_trace_hash, ticket_number, free_text, created, modified)
VALUES ('cb7e65fc-1bb8-4192-af41-ba7bc6fdc395', 'MESSAGE_NOT_FOUND', 'MessageProcessingFailedEvent', 'wvs-communication-service',
        'MESSAGE_NOT_FOUND: No message found with dbMessageId=61314b01-a46a-4269-97d5-fab5690045f2',
        'stack-trace-hash-1', '', 'okokoko', '2024-09-23 15:30:14.993711 +00:00', '2024-10-07 12:06:57.159264 +00:00');

INSERT INTO error_group (id, error_code, event_name, error_publisher, error_message, error_stack_trace_hash, ticket_number, free_text, created, modified)
VALUES ('4709d1b8-8585-4e13-91dd-eeab2392be71', 'REST_CALL_FAILED', 'MessageProcessingFailedEvent', 'wvs-communication-service',
        'REST_CALL_FAILED: request url=http://localhost:8121/ncts-declaration/api/v2/goods-declarations/20DEP98IUWWUG00NN5 failed with status 409 CONFLICT ',
        'stack-trace-hash-2', 'TAPAS-144', 'iaa am', '2024-09-23 15:50:29.661890 +00:00', '2024-10-07 14:23:35.734587 +00:00');

INSERT INTO error_group (id, error_code, event_name, error_publisher, error_message, error_stack_trace_hash, ticket_number, free_text, created, modified)
VALUES ('cb7e65fc-1bb8-4192-af41-ba7bc6fdc000', 'MESSAGE_NOT_FOUND', 'MessageProcessingFailedEvent', 'wvs-communication-service',
        'MESSAGE_NOT_FOUND: No message found with dbMessageId=61314b01-a46a-4269-97d5-fab5690045f2',
        'stack-trace-hash-3', '', 'okokoko', '2024-09-23 15:30:14.993711 +00:00', '2024-10-07 12:06:57.159264 +00:00');
