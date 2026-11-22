CREATE TABLE beta_feedback (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  digest text NOT NULL,
  report jsonb NOT NULL,
  priority text NOT NULL DEFAULT 'P2',
  status text NOT NULL DEFAULT 'open',
  component text NOT NULL DEFAULT '',
  reproduction text NOT NULL DEFAULT 'unverified',
  regression_test text NOT NULL DEFAULT '',
  resolution text NOT NULL DEFAULT '',
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(owner_id,digest)
);
CREATE INDEX beta_feedback_created ON beta_feedback(created_at);
