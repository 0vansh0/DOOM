export class ApiError extends Error {
  constructor(message: string, readonly status: number, readonly code?: string) { super(message); }
}

export function resolveMarketplaceApiBase(configured: string | undefined, development: boolean): string {
  if (!configured?.trim()) return development ? 'http://127.0.0.1:8787' : '';
  let parsed: URL;
  try { parsed = new URL(configured); }
  catch { throw new Error('VITE_MARKETPLACE_API_URL must be an absolute URL.'); }
  const localHttp = development && parsed.protocol === 'http:' && ['localhost', '127.0.0.1', '::1'].includes(parsed.hostname);
  if (parsed.protocol !== 'https:' && !localHttp) throw new Error('Marketplace API must use HTTPS outside local development.');
  if (!development && (['localhost', '127.0.0.1', '::1'].includes(parsed.hostname) || parsed.hostname.endsWith('.invalid')))
    throw new Error('Production portal builds cannot target localhost or a reserved .invalid hostname.');
  return parsed.toString().replace(/\/$/, '');
}

export const apiBase = resolveMarketplaceApiBase(import.meta.env.VITE_MARKETPLACE_API_URL as string | undefined, import.meta.env.DEV);

function apiUrl(path: string) {
  if (!apiBase) throw new ApiError('Marketplace API is not configured. Set an approved HTTPS API URL for this portal build.', 0, 'API_NOT_CONFIGURED');
  return `${apiBase}${path}`;
}

export async function request<T>(path: string, token?: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  if (token) headers.set('authorization', `Bearer ${token}`);
  if (init.body && !(init.body instanceof FormData) && !headers.has('content-type')) headers.set('content-type', 'application/json');
  const target = apiUrl(path);
  let response: Response;
  try { response = await fetch(target, { ...init, headers, signal: init.signal, redirect: 'error' }); }
  catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error;
    throw new ApiError('Marketplace API is unreachable. Check the local API service and connection.', 0);
  }
  const payload = await response.json().catch(() => ({})) as { data?: T; error?: { code?: string; message?: string } };
  if (!response.ok) throw new ApiError(payload.error?.message || `Request failed (${response.status}).`, response.status, payload.error?.code);
  return payload.data as T;
}

export async function requestBlob(path: string, token: string): Promise<{ blob: Blob; sha256: string | null }> {
  const target = apiUrl(path);
  let response: Response;
  try { response = await fetch(target, { headers: { authorization: `Bearer ${token}` }, cache: 'no-store', redirect: 'error' }); }
  catch { throw new ApiError('Marketplace API is unreachable. Check the local API service and connection.', 0); }
  if (!response.ok) {
    const payload = await response.json().catch(() => ({})) as { error?: { code?: string; message?: string } };
    throw new ApiError(payload.error?.message || `Download failed (${response.status}).`, response.status, payload.error?.code);
  }
  if (response.headers.get('content-type')?.split(';')[0] !== 'application/vnd.doom.mod') throw new ApiError('The review download had an unexpected content type.', response.status);
  return { blob: await response.blob(), sha256: response.headers.get('x-doom-sha256') };
}

export function uploadPackage(path: string, token: string, file: File, onProgress: (percent: number) => void, signal?: AbortSignal): Promise<unknown> {
  let target: string;
  try { target = apiUrl(path); } catch (error) { return Promise.reject(error); }
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    const form = new FormData(); form.set('package', file, file.name);
    xhr.open('POST', target); xhr.setRequestHeader('authorization', `Bearer ${token}`);
    xhr.upload.onprogress = event => { if (event.lengthComputable) onProgress(Math.round(event.loaded * 100 / event.total)); };
    const abort = () => xhr.abort(); signal?.addEventListener('abort', abort, { once: true });
    xhr.onload = () => {
      signal?.removeEventListener('abort', abort);
      let payload: { data?: unknown; error?: { message?: string; code?: string } } = {};
      try { payload = JSON.parse(xhr.responseText) as typeof payload; } catch { /* mapped below */ }
      if (xhr.status >= 200 && xhr.status < 300) resolve(payload.data);
      else reject(new ApiError(payload.error?.message || `Upload failed (${xhr.status}).`, xhr.status, payload.error?.code));
    };
    xhr.onerror = () => reject(new ApiError('Package upload failed due to a network error.', 0));
    xhr.onabort = () => reject(new DOMException('Upload cancelled.', 'AbortError'));
    xhr.send(form);
  });
}
