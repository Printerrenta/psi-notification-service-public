CREATE TABLE IF NOT EXISTS notifications (
 event_id UUID PRIMARY KEY,
 aggregate_id UUID NOT NULL,
 owner_subject VARCHAR(255),
 event_type VARCHAR(80) NOT NULL,
 occurred_at TIMESTAMPTZ NOT NULL,
 received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 read_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS notifications_owner_idx ON notifications(owner_subject,occurred_at DESC,event_id);
