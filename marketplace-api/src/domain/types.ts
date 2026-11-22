export type AppSubmissionStatus = 'DRAFT'|'UPLOADED'|'VALIDATING'|'VALIDATION_FAILED'|'PENDING_REVIEW'|'APPROVED'|'REJECTED'|'PUBLISHED'|'QUARANTINED';
export type UserRole = 'DEVELOPER'|'REVIEWER'|'ADMIN';
export type Principal = { id: string; email: string; role: UserRole };
