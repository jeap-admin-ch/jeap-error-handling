ALTER TABLE error ADD original_trace_context_trace_id bigint;
ALTER TABLE error ADD original_trace_context_span_id bigint;
ALTER TABLE error ADD original_trace_context_parent_span_id bigint;
