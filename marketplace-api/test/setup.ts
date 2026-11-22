process.env.NODE_ENV='test';
process.env.DATABASE_URL??='postgres://doom:doom_local_only@127.0.0.1:5438/doom_marketplace';
process.env.JWT_SECRET??='local-tests-only-change-this-secret-at-least-32';
process.env.S3_ENDPOINT??='http://127.0.0.1:9000';
process.env.S3_ACCESS_KEY_ID??='doom_local';
process.env.S3_SECRET_ACCESS_KEY??='doom_local_secret_change_me';
process.env.S3_QUARANTINE_BUCKET??='doom-quarantine';
process.env.S3_PUBLISHED_BUCKET??='doom-published';
