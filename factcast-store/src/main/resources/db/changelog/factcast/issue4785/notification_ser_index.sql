-- this one is need for "all unseen"-style queries and max(ser)
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_notification_ser ON notification (ser);
