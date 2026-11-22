export type Role = 'DEVELOPER' | 'REVIEWER' | 'ADMIN';
export interface User { id: string; email: string; role: Role }
export interface Submission {
  id: string; ownerId?: string; modId: string | null; status: string; sha256: string | null;
  byteSize?: number | null; originalName?: string; manifest?: Record<string, unknown> | null;
  definition?: Record<string, unknown> | null; validationSummary?: Record<string, unknown> | null;
  validations?: Array<{ passed: boolean; validatorVersion: string; diagnostics: unknown[]; createdAt: string }>;
  moderationDecisions?: Array<{ decision: string; reason: string; createdAt: string }>;
  createdAt: string; updatedAt: string;
}
export interface QueueItem { id: string; modId: string | null; status: string; ownerId: string; createdAt: string; name: string | null; version: string | null }
export interface UserReport { id: string; reporterId: string | null; modId: string; version: string | null; reason: string; status: string; createdAt: string }
export interface AuditEvent { id: string; actorId: string | null; action: string; subjectType: string; subjectId: string | null; createdAt: string; details?: Record<string, unknown> }
export interface Profile { userId: string; displayName: string; bio: string; website: string | null }

export function validateDoommodFile(file: Pick<File, 'name' | 'size'>): string | null {
  if (!file.name.toLowerCase().endsWith('.doommod')) return 'Choose a .doommod package.';
  if (file.size < 1 || file.size > 10 * 1024 * 1024) return 'Package size must be between 1 byte and 10 MB.';
  return null;
}

export function statusTone(status: string): 'good' | 'warn' | 'bad' | 'neutral' {
  if (['PUBLISHED', 'APPROVED', 'VALIDATION_PASSED', 'AVAILABLE'].includes(status)) return 'good';
  if (['PENDING_REVIEW', 'UPLOADED', 'VALIDATING', 'DRAFT'].includes(status)) return 'warn';
  if (['REJECTED', 'VALIDATION_FAILED', 'QUARANTINED'].includes(status)) return 'bad';
  return 'neutral';
}

export function humanBytes(value: number | null | undefined): string {
  if (!value || value < 0) return '—';
  return value < 1024 * 1024 ? `${(value / 1024).toFixed(0)} KB` : `${(value / (1024 * 1024)).toFixed(2)} MB`;
}
