import React, { useCallback, useEffect, useState } from 'react';
import { api, fmtTime } from '../api.js';
import { Banner, Chip, Empty, PageHead, errResult, okResult, resultTone } from '../components/ui.jsx';

const DEMO_EMAILS = ['admin@tenant-a.com', 'user@tenant-a.com', 'user@tenant-b.com'];

function ShareTable({ rows, outgoing, onRevoke }) {
  if (!rows) return <div className="boot">Loading...</div>;
  if (!rows.length) return <Empty>{outgoing ? 'Nothing shared yet.' : 'Nobody has shared a document with you.'}</Empty>;
  return (
    <div className="table-wrap">
      <table>
        <thead><tr><th>Document</th><th>{outgoing ? 'Shared with' : 'Shared by'}</th><th>Permission</th><th>Created</th><th>Status</th>{outgoing && <th className="right">Action</th>}</tr></thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.id}>
              <td><strong>{r.documentName}</strong></td>
              <td>{outgoing ? <>{r.sharedWithEmail}<div className="sub">{r.sharedWithTenant}</div></> : r.sharedByEmail}</td>
              <td><Chip tone="brand">{r.permission}</Chip></td>
              <td>{fmtTime(r.createdAt)}</td>
              <td><Chip tone={resultTone(r.status)}>{r.status}</Chip>{r.revokedAt && <div className="sub">{fmtTime(r.revokedAt)}</div>}</td>
              {outgoing && <td className="right">{r.status === 'ACTIVE' && <button className="danger" onClick={() => onRevoke(r)}>Revoke</button>}</td>}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export default function Sharing() {
  const [docs, setDocs] = useState([]);
  const [out, setOut] = useState(null);
  const [inc, setInc] = useState(null);
  const [result, setResult] = useState(null);
  const [docId, setDocId] = useState('');
  const [email, setEmail] = useState('');
  const [perm, setPerm] = useState('DOWNLOAD');

  const load = useCallback(async () => {
    try {
      const [d, o, i] = await Promise.all([api('/documents'), api('/shares/outgoing'), api('/shares/incoming')]);
      const mine = d.filter((x) => x.canShare);
      setDocs(mine); setOut(o); setInc(i);
      setDocId((cur) => cur || mine[0]?.id || '');
    } catch (e) { setResult(errResult(e)); }
  }, []);
  useEffect(() => { load(); }, [load]);

  const share = async (e) => {
    e.preventDefault();
    try {
      const r = await api(`/documents/${docId}/shares`, { method: 'POST', body: { email, permission: perm } });
      setResult(okResult(`Shared "${r.documentName}" with ${r.sharedWithEmail}`, `Permission ${r.permission}.`));
      setEmail(''); load();
    } catch (ex) { setResult(errResult(ex)); }
  };

  const revoke = async (r) => {
    try {
      await api(`/shares/${r.id}/revoke`, { method: 'POST' });
      setResult(okResult(`Revoked ${r.sharedWithEmail}'s access to "${r.documentName}"`, 'Their next request returns 403 \u2014 Access Denied. The check runs on the server.'));
      load();
    } catch (ex) { setResult(errResult(ex)); }
  };

  return (
    <>
      <PageHead title="Sharing & access" sub="Grant access to a specific person, then take it away. Both are enforced by the backend." />
      <Banner result={result} onClose={() => setResult(null)} />

      <section className="panel">
        <div className="panel-head"><h2>Share a document</h2></div>
        {docs.length === 0 ? <Empty>You don't own or administer any documents yet.</Empty> : (
          <form className="inline-form" onSubmit={share}>
            <select value={docId} onChange={(e) => setDocId(e.target.value)} aria-label="Document">
              {docs.map((d) => <option key={d.id} value={d.id}>{d.name} (v{d.version})</option>)}
            </select>
            <input list="emails2" placeholder="email, e.g. user@tenant-b.com" value={email} onChange={(e) => setEmail(e.target.value)} aria-label="Share with" />
            <datalist id="emails2">{DEMO_EMAILS.map((m) => <option key={m} value={m} />)}</datalist>
            <select value={perm} onChange={(e) => setPerm(e.target.value)} aria-label="Permission">
              <option value="DOWNLOAD">DOWNLOAD</option><option value="VIEW">VIEW</option>
            </select>
            <button className="primary" disabled={!email || !docId}>Share</button>
          </form>
        )}
      </section>

      <section className="panel">
        <div className="panel-head"><h2>Shared from your tenant</h2></div>
        <ShareTable rows={out} outgoing onRevoke={revoke} />
      </section>

      <section className="panel">
        <div className="panel-head"><h2>Shared with you</h2></div>
        <ShareTable rows={inc} />
      </section>
    </>
  );
}
