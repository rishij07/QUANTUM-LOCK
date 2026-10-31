const TOKEN_KEY = 'ql_token';

export const getToken = () => localStorage.getItem(TOKEN_KEY);
export const setToken = (t) => (t ? localStorage.setItem(TOKEN_KEY, t) : localStorage.removeItem(TOKEN_KEY));

export class ApiError extends Error {
  constructor(status, body) {
    super(body?.message || `Request failed (${status})`);
    this.status = status;
    this.body = body || {};
  }
}

/** All calls go through here. The token is the ONLY identity we send: no tenant id, no role, nothing to trust. */
export async function api(path, { method = 'GET', body, form, raw = false } = {}) {
  const headers = {};
  const token = getToken();
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body) headers['Content-Type'] = 'application/json';
  let res;
  try {
    res = await fetch(`/api${path}`, { method, headers, body: form || (body ? JSON.stringify(body) : undefined) });
  } catch {
    throw new ApiError(0, { message: 'Backend unreachable', detail: 'Is the API container running?' });
  }
  if (!res.ok) {
    let err = {};
    try { err = await res.json(); } catch { /* empty body */ }
    if (res.status === 401 && token && !path.startsWith('/auth')) {
      setToken(null);
      window.location.assign('/login');
    }
    throw new ApiError(res.status, err);
  }
  if (raw) return res;
  return res.status === 204 ? null : res.json();
}

export const fmtBytes = (n) => (n < 1024 ? `${n} B` : n < 1048576 ? `${(n / 1024).toFixed(1)} KB` : `${(n / 1048576).toFixed(1)} MB`);
export const fmtTime = (t) => (t ? new Date(t).toLocaleString([], { dateStyle: 'medium', timeStyle: 'medium' }) : '\u2014');
export const fmtDate = (t) => (t ? new Date(t).toLocaleDateString([], { dateStyle: 'medium' }) : '\u2014');
