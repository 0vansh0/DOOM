# Disaster recovery proposal

Recovery sequence: declare incident; restrict ingress and credentials; preserve logs and database/object snapshots; provision an isolated recovery environment; verify signed immutable image provenance; restore PostgreSQL to a new database; restore quarantine and published objects from protected backups; verify migration state and checksums; run health/readiness and catalog/download integrity checks; then obtain explicit operational approval before traffic cutover.

Do not restore over an existing production database during routine verification. The proposed RPO (24 hours) and RTO (8 hours) are planning targets, not measured guarantees. Recovery remains unverified until isolated restore drills cover both database and objects.
