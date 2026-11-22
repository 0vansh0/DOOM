ALTER TABLE beta_feedback
  ADD COLUMN updated_at timestamptz NOT NULL DEFAULT now();

CREATE INDEX beta_feedback_status_updated_idx ON beta_feedback(status, updated_at);

ALTER TABLE beta_feedback
  ADD CONSTRAINT beta_feedback_priority_valid
    CHECK (priority IN ('P0','P1','P2','P3')) NOT VALID,
  ADD CONSTRAINT beta_feedback_status_valid
    CHECK (status IN ('open','in-progress','resolved')) NOT VALID,
  ADD CONSTRAINT beta_feedback_reproduction_valid
    CHECK (reproduction IN ('unverified','reproduced','cannot reproduce')) NOT VALID;

CREATE OR REPLACE FUNCTION delete_feedback_audit_on_feedback_delete()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  DELETE FROM audit_events
    WHERE subject_type = 'feedback' AND subject_id = OLD.id::text;
  RETURN OLD;
END;
$$;

CREATE TRIGGER beta_feedback_delete_audit
AFTER DELETE ON beta_feedback
FOR EACH ROW EXECUTE FUNCTION delete_feedback_audit_on_feedback_delete();
