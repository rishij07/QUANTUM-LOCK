import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { RefreshCw, ShieldCheck, ShieldAlert, FlaskConical } from 'lucide-react';
import { api, fmtTime } from '../api.js';
import { useAuth } from '../auth.jsx';
import { Banner, Empty, PageHead, Result, errResult } from '../components/ui.jsx';

export default function Audit() {
  const { user } = useAuth();
  const [rows, setRows] = useState(null);
  const [chain, setChain] = useState(null);
  const [sim, setSim] = useState(null);
  const [err, setErr] = useState(null);
  const [action, setAction] = useState('ALL');
  const [open, setOpen] = useState(null);

  const load = useCallback(async () => {
    try {
      const [r, c] = await Promise.all([api('/audit?limit=200'), api('/audit/verify')]);
      setRows(r); setChain(c);
    } catch (e) { setErr(errResult(e)); }
  }, []);
  useEffect(() => { load(); }, [load]);

  const actions = useMemo(() => ['ALL', ...new Set((rows || []).map((r) => r.action))], [rows]);
  const shown = (rows || []).filter((r) => action === 'ALL' || r.action === action);

  return (
    <>
      <PageHead title="Audit logs" sub={user.role === 'ADMIN' ? `Every security event in ${user.tenantName}.` : 'Your own security events.'}>
        <button className="ghost" onClick={load}><RefreshCw size={16} /> Refresh</button>
        <button className="ghost" onClick={async () => setChain(await api('/audit/verify'))}><ShieldCheck size={16} /> Verify chain</button>
        <button className="ghost" onClick={async () => setSim(await api('/audit/simulate-tamper', { method: 'POST' }))}><FlaskConical size={16} /> Simulate log tampering</button>
      </PageHead>
      <Banner result={err} onClose={() => setErr(null)} />

      {chain && (
        <div className={`banner ${chain.valid ? 'ok' : 'blocked'}`}>
          {chain.valid ? <ShieldCheck size={20} /> : <ShieldAlert size={20} />}
          <div><strong>{chain.valid ? 'CHAIN INTACT' : 'CHAIN BROKEN'}</strong><p>{chain.message}</p></div>
        </div>
      )}
      {sim && (
        <div className={`banner ${sim.detected ? 'detected' : 'blocked'}`}>
          <FlaskConical size={20} />
          <div>
            <strong>{sim.detected ? `DETECTED \u2014 forged event #${sim.targetId} fails verification` : 'Not detected'}</strong>
            <p>{sim.explanation}</p>
            <p className="mono small">stored {sim.storedHash?.slice(0, 24)}… &nbsp;vs&nbsp; recomputed {sim.recomputedHash?.slice(0, 24)}…</p>
          </div>
          <button className="link" onClick={() => setSim(null)}>Dismiss</button>
        </div>
      )}

      <section className="panel">
        <div className="panel-head">
          <h2>Events</h2>
          <select value={action} onChange={(e) => setAction(e.target.value)} aria-label="Filter by action">
            {actions.map((a) => <option key={a}>{a}</option>)}
          </select>
        </div>
        {!rows ? <div className="boot">Loading...</div> : shown.length === 0 ? <Empty>No events match.</Empty> : (
          <div className="table-wrap">
            <table className="audit">
              <thead><tr><th>Time</th><th>User</th><th>Tenant</th><th>Action</th><th>Resource</th><th>Result</th></tr></thead>
              <tbody>
                {shown.map((r) => (
                  <React.Fragment key={r.id}>
                    <tr className="clickable" onClick={() => setOpen(open === r.id ? null : r.id)}>
                      <td className="mono nowrap">{fmtTime(r.ts)}</td>
                      <td>{r.user}</td>
                      <td>{r.tenant}</td>
                      <td className="mono">{r.action}</td>
                      <td>{r.resource}</td>
                      <td><Result value={r.result} /></td>
                    </tr>
                    {open === r.id && (
                      <tr className="detail-row"><td colSpan={6}>
                        <div className="mono small">#{r.id} · {r.detail || 'no detail'}</div>
                        <div className="mono small dim">prev {r.prevHash}</div>
                        <div className="mono small dim">hash&nbsp; {r.hash}</div>
                      </td></tr>
                    )}
                  </React.Fragment>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </>
  );
}
