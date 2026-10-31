import React, { useState } from 'react';
import { Building2, Bug, UserX, KeyRound, Copy, ScrollText, Play, Zap } from 'lucide-react';
import { api } from '../api.js';
import { Banner, Chip, PageHead, errResult, resultTone } from '../components/ui.jsx';

const ATTACKS = [
  { id: 'cross-tenant', title: 'Cross-Tenant Access', Icon: Building2, blurb: 'Request a document that belongs to the other tenant, using its exact ID.', expect: 'BLOCKED' },
  { id: 'tampering', title: 'Ciphertext Tampering', Icon: Bug, blurb: 'Modify one byte of an encrypted file and ask the vault to decrypt it.', expect: 'DETECTED' },
  { id: 'revoked', title: 'Revoked User Access', Icon: UserX, blurb: 'Share a document, let you read it, revoke the share, then try again.', expect: 'DENIED' },
  { id: 'invalid-jwt', title: 'Invalid / Expired JWT', Icon: KeyRound, blurb: 'Present an expired, forged-signature and unsigned token.', expect: 'DENIED' },
  { id: 'duplicate', title: 'Duplicate Upload', Icon: Copy, blurb: 'Replay the exact bytes of an existing document as a new upload.', expect: 'DETECTED' },
  { id: 'audit-tamper', title: 'Audit Log Tampering', Icon: ScrollText, blurb: 'Rewrite a past DENIED event as SUCCESS to hide it.', expect: 'DETECTED' },
];

export default function Attacks() {
  const [results, setResults] = useState({});
  const [running, setRunning] = useState({});
  const [err, setErr] = useState(null);

  const run = async (id) => {
    setRunning((r) => ({ ...r, [id]: true }));
    try {
      const res = await api(`/attacks/${id}`, { method: 'POST' });
      if (id === 'invalid-jwt' && res.probeToken) {
        // Live proof: fire the forged token at a real endpoint and record what comes back.
        const probe = await fetch('/api/documents', { headers: { Authorization: `Bearer ${res.probeToken}` } });
        res.steps = [...res.steps, `Live request with the forged token to GET /api/documents \u2014 HTTP ${probe.status}`];
      }
      setResults((r) => ({ ...r, [id]: res }));
    } catch (e) { setErr(errResult(e)); }
    finally { setRunning((r) => ({ ...r, [id]: false })); }
  };

  const runAll = async () => { for (const a of ATTACKS) await run(a.id); };

  return (
    <>
      <PageHead title="Attack Simulation Centre" sub="Real attacks against the real code paths. Nothing here is mocked, and every attempt lands in the audit log.">
        <button className="primary" onClick={runAll}><Zap size={16} /> Run all attacks</button>
      </PageHead>
      <Banner result={err} onClose={() => setErr(null)} />
      <div className="attack-grid">
        {ATTACKS.map(({ id, title, Icon, blurb }) => {
          const r = results[id];
          return (
            <article key={id} className={`attack ${r ? resultTone(r.verdict) : ''}`}>
              <div className="attack-head"><Icon size={20} /><h2>{title}</h2></div>
              <p>{blurb}</p>
              <button className="ghost" disabled={running[id]} onClick={() => run(id)}><Play size={14} /> {running[id] ? 'Running...' : 'Run attack'}</button>
              {r && (
                <div className="attack-result">
                  <div className="verdict-row">
                    <Chip tone={resultTone(r.verdict)}>{r.verdict}</Chip>
                    {r.httpStatus > 0 && <span className="mono">HTTP {r.httpStatus}</span>}
                  </div>
                  <strong>{r.summary}</strong>
                  <ol className="steps">{r.steps.map((s, i) => <li key={i}>{s}</li>)}</ol>
                  <p className="why"><span>Why it failed:</span> {r.explanation}</p>
                </div>
              )}
            </article>
          );
        })}
      </div>
    </>
  );
}
