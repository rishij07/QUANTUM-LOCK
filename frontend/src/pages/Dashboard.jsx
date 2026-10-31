import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ShieldCheck, ShieldAlert, KeyRound, Network, FileLock2 } from 'lucide-react';
import { api, fmtTime } from '../api.js';
import { AccessChip, ClassChip, Banner, errResult, PageHead, Result } from '../components/ui.jsx';

function Stat({ label, value, note, tone }) {
  return (
    <div className={`stat ${tone || ''}`}>
      <div className="stat-value">{value}</div>
      <div className="stat-label">{label}</div>
      {note && <div className="stat-note">{note}</div>}
    </div>
  );
}

export default function Dashboard() {
  const [d, setD] = useState(null);
  const [err, setErr] = useState(null);

  useEffect(() => { api('/dashboard').then(setD).catch((e) => setErr(errResult(e))); }, []);
  if (err) return <Banner result={err} />;
  if (!d) return <div className="boot">Reading vault status...</div>;

  return (
    <>
      <PageHead title="Dashboard" sub={`Signed in as ${d.user.displayName} \u00b7 ${d.user.tenantName}`} />

      <section className="posture" aria-label="Security posture">
        <div className="posture-item">
          {d.chain.valid ? <ShieldCheck size={22} className="t-ok" /> : <ShieldAlert size={22} className="t-blocked" />}
          <div><strong>{d.chain.valid ? 'Audit chain intact' : 'Audit chain BROKEN'}</strong><span>{d.chain.total} events verified with SHA-256</span></div>
        </div>
        <div className="posture-item"><FileLock2 size={22} className="t-brand" /><div><strong>AES-256-GCM</strong><span>unique DEK per document version</span></div></div>
        <div className="posture-item"><KeyRound size={22} className="t-brand" /><div><strong>KEK {d.keyId}</strong><span>wraps every DEK via the KMS layer</span></div></div>
        <div className="posture-item"><Network size={22} className="t-brand" /><div><strong>Tenant isolation enforced</strong><span>tenant derived from your identity</span></div></div>
      </section>

      <section className="stats">
        <Stat label="Documents you can see" value={d.totalDocuments} />
        <Stat label="Shared with you" value={d.sharedWithMe} />
        <Stat label="Shared by your tenant" value={d.sharedByMe} />
        <Stat label="Blocked or denied, last 24h" value={d.securityEvents24h} note={`of ${d.totalEvents24h} logged events`} tone={d.securityEvents24h ? 'warn' : ''} />
      </section>

      <div className="two-col">
        <section className="panel">
          <div className="panel-head"><h2>Recent uploads</h2><Link to="/documents">All documents</Link></div>
          <div className="table-wrap">
            <table>
              <thead><tr><th>Document</th><th>Class</th><th>Version</th><th>Access</th></tr></thead>
              <tbody>
                {d.recentUploads.map((x) => (
                  <tr key={x.id}>
                    <td>{x.name}<div className="sub">{x.ownerEmail}</div></td>
                    <td><ClassChip value={x.classification} /></td>
                    <td className="mono">v{x.version}</td>
                    <td><AccessChip basis={x.access} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>

        <section className="panel">
          <div className="panel-head"><h2>Security events</h2><Link to="/audit">Audit logs</Link></div>
          <ul className="events">
            {d.recentEvents.map((e) => (
              <li key={e.id}>
                <Result value={e.result} />
                <div><strong className="mono">{e.action}</strong><div className="sub">{e.user} · {fmtTime(e.ts)}</div></div>
              </li>
            ))}
          </ul>
        </section>
      </div>
    </>
  );
}
