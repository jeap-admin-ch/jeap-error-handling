-- Adds the sampling decision captured from the origin trace so error resend through TraceContextUpdater
-- preserves it. Rows migrated from a pre-OTel jEAP version carry NULL and are treated as sampled (legacy default)
-- on replay.
ALTER TABLE error ADD original_trace_context_sampled boolean;
