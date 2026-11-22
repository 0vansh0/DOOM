import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Portal } from './portal';
import { statusTone, validateDoommodFile } from './types';

describe('developer portal', () => {
  beforeEach(() => vi.restoreAllMocks());

  it('validates local package extension and size before upload', () => {
    expect(validateDoommodFile({ name: 'sample.zip', size: 50 })).toMatch(/\.doommod/);
    expect(validateDoommodFile({ name: 'sample.doommod', size: 10 * 1024 * 1024 + 1 })).toMatch(/10 MB/);
    expect(validateDoommodFile({ name: 'sample.doommod', size: 200 })).toBeNull();
  });

  it('maps backend lifecycle states without inventing metrics', () => {
    expect(statusTone('PENDING_REVIEW')).toBe('warn');
    expect(statusTone('PUBLISHED')).toBe('good');
    expect(statusTone('QUARANTINED')).toBe('bad');
    expect(statusTone('UNKNOWN_NEW_STATE')).toBe('neutral');
  });

  it('authenticates through the backend and does not expose moderator navigation to developers', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.endsWith('/v1/auth/login')) return new Response(JSON.stringify({ data: { accessToken: 'short-lived-test-token', expiresIn: 900, user: { id: 'u-1', email: 'dev@example.test', role: 'DEVELOPER' } } }), { status: 200 });
      if (url.endsWith('/v1/submissions')) return new Response(JSON.stringify({ data: [] }), { status: 200 });
      throw new Error(`Unexpected request ${url}`);
    });
    const user = userEvent.setup(); render(<Portal/>);
    await user.type(screen.getByLabelText('Email address'), 'dev@example.test');
    await user.type(screen.getByLabelText('Password'), 'correct horse battery staple');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByText('Ship your next DOOM mod.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'My submissions' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Review queue' })).not.toBeInTheDocument();
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/v1/submissions'), expect.objectContaining({ headers: expect.any(Headers) })));
  });

  it('renders login error from the backend and supports registration mode', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({ error: { code: 'INVALID_CREDENTIALS', message: 'Email or password is incorrect.' } }), { status: 401 }));
    const user = userEvent.setup(); render(<Portal/>);
    await user.type(screen.getByLabelText('Email address'), 'bad@example.test');
    await user.type(screen.getByLabelText('Password'), 'wrong');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Email or password is incorrect.');
    await user.click(screen.getByRole('button', { name: 'Create an account' }));
    expect(screen.getByRole('heading', { name: 'Create developer account' })).toBeInTheDocument();
    expect(screen.getByLabelText('Developer display name')).toBeInTheDocument();
  });
});
