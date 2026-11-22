CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE TYPE user_role AS ENUM ('DEVELOPER','REVIEWER','ADMIN');
CREATE TYPE submission_status AS ENUM ('DRAFT','UPLOADED','VALIDATING','VALIDATION_FAILED','PENDING_REVIEW','APPROVED','REJECTED','PUBLISHED','QUARANTINED');
CREATE TABLE users (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), email varchar(320) NOT NULL, password_hash text NOT NULL, role user_role NOT NULL DEFAULT 'DEVELOPER', disabled boolean NOT NULL DEFAULT false, created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now());
CREATE UNIQUE INDEX users_email_unique ON users(lower(email));
CREATE TABLE developer_profiles (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), user_id uuid NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE, display_name varchar(80) NOT NULL, bio varchar(500) NOT NULL DEFAULT '', website varchar(2048), created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE mods (id varchar(64) PRIMARY KEY, owner_id uuid NOT NULL REFERENCES users(id), name varchar(80) NOT NULL, summary varchar(500) NOT NULL, category varchar(40) NOT NULL DEFAULT 'other', created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now());
CREATE INDEX mods_owner_idx ON mods(owner_id); CREATE INDEX mods_name_idx ON mods(name);
CREATE TABLE mod_versions (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), mod_id varchar(64) NOT NULL REFERENCES mods(id) ON DELETE RESTRICT, version varchar(64) NOT NULL, manifest_schema integer NOT NULL, definition_version integer NOT NULL, min_android_api integer NOT NULL, capabilities jsonb NOT NULL, compatibility jsonb NOT NULL DEFAULT '{}', sha256 varchar(64) NOT NULL, object_key text NOT NULL, published_at timestamptz, quarantined_at timestamptz, created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(mod_id,version), UNIQUE(object_key));
CREATE INDEX mod_versions_published_idx ON mod_versions(published_at);
CREATE TABLE submissions (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), owner_id uuid NOT NULL REFERENCES users(id), mod_id varchar(64), status submission_status NOT NULL DEFAULT 'DRAFT', quarantine_key text, original_name varchar(255) NOT NULL DEFAULT 'upload.doommod', sha256 varchar(64), byte_size bigint, manifest jsonb, definition jsonb, validation_summary jsonb, created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now());
CREATE INDEX submissions_owner_status_idx ON submissions(owner_id,status); CREATE INDEX submissions_mod_idx ON submissions(mod_id);
CREATE UNIQUE INDEX submissions_owner_sha_unique ON submissions(owner_id,sha256) WHERE sha256 IS NOT NULL;
CREATE OR REPLACE FUNCTION enforce_submission_transition() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
  IF OLD.status = NEW.status THEN RETURN NEW; END IF;
  IF NOT (CASE OLD.status
    WHEN 'DRAFT' THEN NEW.status = 'UPLOADED'
    WHEN 'UPLOADED' THEN NEW.status = 'VALIDATING'
    WHEN 'VALIDATING' THEN NEW.status IN ('VALIDATION_FAILED','PENDING_REVIEW')
    WHEN 'VALIDATION_FAILED' THEN NEW.status = 'UPLOADED'
    WHEN 'PENDING_REVIEW' THEN NEW.status IN ('APPROVED','REJECTED')
    WHEN 'APPROVED' THEN NEW.status = 'PUBLISHED'
    WHEN 'REJECTED' THEN NEW.status = 'UPLOADED'
    WHEN 'PUBLISHED' THEN NEW.status = 'QUARANTINED'
    ELSE false END) THEN RAISE EXCEPTION 'Illegal submission status transition: % -> %', OLD.status, NEW.status; END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER submission_transition_guard BEFORE UPDATE OF status ON submissions FOR EACH ROW EXECUTE FUNCTION enforce_submission_transition();
CREATE TABLE validation_results (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), submission_id uuid NOT NULL REFERENCES submissions(id) ON DELETE CASCADE, validator_version varchar(40) NOT NULL, passed boolean NOT NULL, diagnostics jsonb NOT NULL, created_at timestamptz NOT NULL DEFAULT now());
CREATE INDEX validation_submission_idx ON validation_results(submission_id);
CREATE TABLE moderation_decisions (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), submission_id uuid NOT NULL REFERENCES submissions(id), reviewer_id uuid NOT NULL REFERENCES users(id), decision varchar(16) NOT NULL CHECK(decision IN ('APPROVE','REJECT')), reason varchar(1000) NOT NULL, created_at timestamptz NOT NULL DEFAULT now()); CREATE INDEX moderation_submission_idx ON moderation_decisions(submission_id);
CREATE TABLE user_reports (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), reporter_id uuid REFERENCES users(id) ON DELETE SET NULL, mod_id varchar(64) NOT NULL REFERENCES mods(id), version varchar(64), reason varchar(1000) NOT NULL, status varchar(16) NOT NULL DEFAULT 'OPEN', created_at timestamptz NOT NULL DEFAULT now()); CREATE INDEX reports_mod_status_idx ON user_reports(mod_id,status);
CREATE TABLE audit_events (id uuid PRIMARY KEY DEFAULT gen_random_uuid(), actor_id uuid REFERENCES users(id) ON DELETE SET NULL, action varchar(80) NOT NULL, subject_type varchar(40) NOT NULL, subject_id varchar(128), details jsonb NOT NULL DEFAULT '{}', created_at timestamptz NOT NULL DEFAULT now()); CREATE INDEX audit_created_idx ON audit_events(created_at); CREATE INDEX audit_subject_idx ON audit_events(subject_type,subject_id);
CREATE TABLE upload_quotas (owner_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE, quota_day varchar(10) NOT NULL, upload_count integer NOT NULL DEFAULT 0, byte_count bigint NOT NULL DEFAULT 0, updated_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY(owner_id,quota_day));
CREATE OR REPLACE FUNCTION protect_published_version() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
  IF OLD.published_at IS NOT NULL THEN
    IF (NEW.mod_id,NEW.version,NEW.manifest_schema,NEW.definition_version,NEW.min_android_api,NEW.capabilities,NEW.compatibility,NEW.sha256,NEW.object_key,NEW.created_at,NEW.published_at)
       IS DISTINCT FROM
       (OLD.mod_id,OLD.version,OLD.manifest_schema,OLD.definition_version,OLD.min_android_api,OLD.capabilities,OLD.compatibility,OLD.sha256,OLD.object_key,OLD.created_at,OLD.published_at) THEN
      RAISE EXCEPTION 'Published mod versions are immutable';
    END IF;
    IF OLD.quarantined_at IS NOT NULL AND NEW.quarantined_at IS DISTINCT FROM OLD.quarantined_at THEN RAISE EXCEPTION 'Quarantine is irreversible'; END IF;
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER mod_versions_immutable BEFORE UPDATE ON mod_versions FOR EACH ROW EXECUTE FUNCTION protect_published_version();
