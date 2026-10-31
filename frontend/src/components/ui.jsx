import React from 'react';
import { ShieldCheck, ShieldX, ShieldAlert } from 'lucide-react';

export const resultTone = (r) => ({
  SUCCESS: 'ok', BLOCKED: 'blocked', DENIED: 'denied', DETECTED: 'detected', 'FAILED CLOSED': 'closed',
  'NOT BLOCKED': 'blocked', FAILED: 'denied', ACTIVE: 'ok', REVOKED: 'blocked',
}[r] || 'muted');

export function Chip({ tone = 'muted', children, title }) {
  return <span className={`chip ${tone}`} title={title}>{children}</span>;
}

export const Result = ({ value }) => <Chip tone={resultTone(value)}>{value}</Chip>;

const ACCESS = {
  OWNER: ['Owner', 'ok'], TENANT_ADMIN: ['Tenant admin', 'ok'], TENANT_MEMBER: ['Tenant access', 'ok'],
  SHARED_DOWNLOAD: ['Shared \u00b7 download', 'brand'], SHARED_VIEW: ['Shared \u00b7 view only', 'denied'],
  RESTRICTED: ['Restricted', 'denied'], REVOKED: ['Revoked', 'blocked'], CROSS_TENANT: ['No access', 'blocked'],
  NOT_OWNER: ['No access', 'blocked'],
};
export function AccessChip({ basis }) {
  const [label, tone] = ACCESS[basis] || [basis, 'muted'];
  return <Chip tone={tone}>{label}</Chip>;
}

export const ClassChip = ({ value }) => (
  <Chip tone={{ CONFIDENTIAL: 'blocked', INTERNAL: 'brand', PUBLIC: 'muted' }[value] || 'muted'}>{value}</Chip>
);

/** Turns a thrown ApiError into something the banner can show. */
export const errResult = (e) => ({ ok: false, status: e.status, title: e.body?.message || e.message, detail: e.body?.detail, code: e.body?.code });
export const okResult = (title, detail, status = 200) => ({ ok: true, status, title, detail });

export function Banner({ result, onClose }) {
  if (!result) return null;
  const tampered = result.code === 'TAMPERING_DETECTED';
  const tone = result.ok ? 'ok' : tampered ? 'detected' : result.status === 403 ? 'blocked' : 'denied';
  const Icon = result.ok ? ShieldCheck : result.status === 403 ? ShieldX : ShieldAlert;
  return (
    <div className={`banner ${tone}`} role="status">
      <Icon size={20} />
      <div>
        <strong>{result.status && !tampered ? `${result.status} \u2014 ` : ''}{result.title}</strong>
        {result.detail && <p>{result.detail}</p>}
      </div>
      {onClose && <button className="link" onClick={onClose}>Dismiss</button>}
    </div>
  );
}

export function PageHead({ title, sub, children }) {
  return (
    <header className="pagehead">
      <div>
        <h1>{title}</h1>
        {sub && <p>{sub}</p>}
      </div>
      <div className="actions">{children}</div>
    </header>
  );
}

export const Empty = ({ children }) => <div className="empty">{children}</div>;

export function Modal({ title, onClose, children, wide }) {
  return (
    <div className="modal-back" onClick={onClose}>
      <div className={`modal ${wide ? 'wide' : ''}`} onClick={(e) => e.stopPropagation()} role="dialog" aria-label={title}>
        <div className="modal-head"><h2>{title}</h2><button className="link" onClick={onClose}>Close</button></div>
        {children}
      </div>
    </div>
  );
}
