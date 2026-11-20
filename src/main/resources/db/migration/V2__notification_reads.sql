CREATE TABLE IF NOT EXISTS notification_reads (
  event_id UUID NOT NULL REFERENCES notifications(event_id) ON DELETE CASCADE,
  reader_subject TEXT NOT NULL,
  read_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (event_id, reader_subject)
);
