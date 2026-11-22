ALTER TABLE beta_feedback
  ADD COLUMN assignee_id uuid REFERENCES users(id) ON DELETE SET NULL;

CREATE INDEX beta_feedback_assignee_status_idx
  ON beta_feedback(assignee_id, status, updated_at);
