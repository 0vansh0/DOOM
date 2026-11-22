import { describe, expect, it } from 'vitest';
import { allowedTransitions, assertTransition } from '../src/domain/transitions.js';

describe('submission lifecycle',()=>{
  it('allows only declared transitions',()=>{
    expect(allowedTransitions.DRAFT).toEqual(['UPLOADED']);
    expect(()=>assertTransition('DRAFT','UPLOADED')).not.toThrow();
    expect(()=>assertTransition('UPLOADED','PUBLISHED')).toThrow('Illegal submission transition');
    expect(()=>assertTransition('PUBLISHED','APPROVED')).toThrow();
    expect(()=>assertTransition('PUBLISHED','QUARANTINED')).not.toThrow();
  });
  it('cannot transition out of quarantine',()=>expect(()=>assertTransition('QUARANTINED','PUBLISHED')).toThrow());
});
