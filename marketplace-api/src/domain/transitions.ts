import type { AppSubmissionStatus } from './types.js';
export const allowedTransitions: Readonly<Record<AppSubmissionStatus, readonly AppSubmissionStatus[]>> = {
  DRAFT: ['UPLOADED'], UPLOADED: ['VALIDATING'], VALIDATING: ['VALIDATION_FAILED','PENDING_REVIEW'],
  VALIDATION_FAILED: ['UPLOADED'], PENDING_REVIEW: ['APPROVED','REJECTED'], APPROVED: ['PUBLISHED'],
  REJECTED: ['UPLOADED'], PUBLISHED: ['QUARANTINED'], QUARANTINED: []
};
export function assertTransition(from: AppSubmissionStatus, to: AppSubmissionStatus): void {
  if (!allowedTransitions[from].includes(to)) throw new Error(`Illegal submission transition: ${from} -> ${to}`);
}
