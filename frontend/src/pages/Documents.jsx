import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { Download, Eye, Share2, FilePlus2, Bug, Crosshair } from 'lucide-react';
import { api, fmtBytes, fmtDate } from '../api.js';
import { AccessChip, Banner, ClassChip, Chip, Empty, Modal, PageHead, errResult, okResult } from '../components/ui.jsx';

const DEMO_EMAILS = ['admin@tenant-a.com', 'user@tenant-a.com', 'user@tenant-b.com'];

async function fetchPlain(id, mode) {
  const res = await api(`/documents/${id}/download?mode=${mode}`, { raw: true });
  return { blob: await res.blob(), type: res.headers.get('content-type') || '', version: res.headers.get('x-document-version') };
}

export default function Documents() {
  const [docs, setDocs] = useState(null);
  const [result, setResult] = useState(null);
  const [viewer, setViewer] = useState(null);
  const [shareFor, setShareFor] = useState(null);
  const [probeId, setProbeId] = useState('');
  const fileRef = useRef(null);
  const versionFor = useRef(null);

  const load = useCallback(() => api('/documents').then(setDocs).catch((e) => setResult(errResult(e))), []);
  useEffect(() => { load(); }, [load]);

  const run = async (fn) => { try { await fn(); } catch (e) { setResult(errResult(e)); load(); } };

  const view = (d) => run(async () => {
    const { blob, type } = await fetchPlain(d.id, 'view');
    const text = type.startsWith('text/') || blob.size < 100000 ? await blob.text() : null;
    setViewer({ doc: d, text: text ?? '(binary content \u2014 use Download)' });
    setResult(okResult(`Decrypted "${d.name}"`, 'AES-256-GCM tag verified. Plaintext released after authorization.'));
  });

  const download = (d) => run(async () => {
    const { blob } = await fetchPlain(d.id, 'download');
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = d.name;
    a.click();
    URL.revokeObjectURL(a.href);
    setResult(okResult(`Downloaded "${d.name}"`, 'Authorization checked, DEK unwrapped, ciphertext verified and decrypted.'));
  });

  const tamper = (d) => run(async () => {
    const r = await api(`/documents/${d.id}/tamper`, { method: 'POST' });
    setResult(okResult(r.tampered ? 'Ciphertext modified in MinIO (1 bit)' : 'Ciphertext restored',
      r.tampered ? 'Now try View or Download on this document: GCM authentication must fail.' : 'The stored object matches its original authentication tag again.'));
    load();
  });

  const onVersionFile = (e) => {
    const f = e.target.files[0];
    const d = versionFor.current;
    e.target.value = '';
    if (!f || !d) return;
    run(async () => {
      const form = new FormData();
      form.append('file', f);
      const r = await api(`/documents/${d.id}/versions`, { method: 'POST', form });
      setResult(okResult(`${d.name} is now v${r.version}`, 'A brand-new DEK was generated for this version.'));
      load();
    });
  };

  const probe = () => run(async () => {
    await fetchPlain(probeId.trim(), 'probe');
    setResult(okResult('Request succeeded', 'You were authorized for this document.'));
  });

  const pickForeign = () => run(async () => {
    const list = await api('/demo/foreign-docs');
    if (!list.length) { setResult(okResult('No foreign documents found', '')); return; }
    setProbeId(list[0].id);
    setResult(okResult(`Loaded ${list[0].tenantName} document ID`, `"${list[0].name}" \u2014 now press Request download.`));
  });

  return (
    <>
      <PageHead title="Documents" sub="Your tenant's documents plus anything explicitly shared with you.">
        <Link className="btn primary" to="/upload"><FilePlus2 size={16} /> Upload</Link>
      </PageHead>
      <Banner result={result} onClose={() => setResult(null)} />
      <input ref={fileRef} type="file" hidden onChange={onVersionFile} />

      <section className="panel">
        {!docs ? <div className="boot">Loading...</div> : docs.length === 0 ? <Empty>No documents yet. Upload one to see it encrypted.</Empty> : (
          <div className="table-wrap">
            <table>
              <thead><tr><th>Document</th><th>Owner</th><th>Classification</th><th>Created</th><th>Version</th><th>Access</th><th className="right">Actions</th></tr></thead>
              <tbody>
                {docs.map((d) => (
                  <tr key={d.id} className={d.tampered ? 'row-tampered' : ''}>
                    <td>
                      <strong>{d.name}</strong>
                      <div className="sub mono">{d.id.slice(0, 8)} · {fmtBytes(d.sizeBytes)}{d.external ? ` \u00b7 from ${d.tenantName}` : ''}</div>
                      {d.tampered && <Chip tone="detected">ciphertext modified</Chip>}
                    </td>
                    <td>{d.ownerEmail}</td>
                    <td><ClassChip value={d.classification} /></td>
                    <td>{fmtDate(d.createdAt)}</td>
                    <td className="mono">v{d.version}</td>
                    <td><AccessChip basis={d.access} /></td>
                    <td className="right">
                      <div className="row-actions">
                        <button className="icon" title="View decrypted" onClick={() => view(d)}><Eye size={16} /></button>
                        <button className="icon" title="Download" onClick={() => download(d)}><Download size={16} /></button>
                        {d.canShare && <button className="icon" title="Share" onClick={() => setShareFor(d)}><Share2 size={16} /></button>}
                        {d.canShare && <button className="icon" title="Upload new version" onClick={() => { versionFor.current = d; fileRef.current.click(); }}><FilePlus2 size={16} /></button>}
                        {d.canShare && <button className="icon warn" title="Demo: flip a bit of the stored ciphertext (click again to restore)" onClick={() => tamper(d)}><Bug size={16} /></button>}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className="panel probe">
        <div className="panel-head"><h2><Crosshair size={16} /> Probe a document by ID</h2></div>
        <p className="sub">Isolation demo: paste any document ID, including one that belongs to another tenant. The server decides from your token, not from anything you send.</p>
        <div className="inline-form">
          <input className="mono" placeholder="document UUID" value={probeId} onChange={(e) => setProbeId(e.target.value)} />
          <button className="ghost" onClick={pickForeign}>Use another tenant's document</button>
          <button className="primary" disabled={!probeId.trim()} onClick={probe}>Request download</button>
        </div>
      </section>

      {viewer && (
        <Modal title={viewer.doc.name} onClose={() => setViewer(null)} wide>
          <pre className="plain">{viewer.text}</pre>
        </Modal>
      )}
      {shareFor && <ShareModal doc={shareFor} onClose={() => setShareFor(null)} onDone={(r) => { setResult(r); setShareFor(null); }} />}
    </>
  );
}

function ShareModal({ doc, onClose, onDone }) {
  const [email, setEmail] = useState('');
  const [perm, setPerm] = useState('DOWNLOAD');
  const [err, setErr] = useState(null);
  const submit = async (e) => {
    e.preventDefault();
    try {
      const r = await api(`/documents/${doc.id}/shares`, { method: 'POST', body: { email, permission: perm } });
      onDone(okResult(`Shared "${doc.name}" with ${r.sharedWithEmail}`, `Permission ${r.permission}. Revoke it any time from Sharing & Access.`));
    } catch (ex) { setErr(errResult(ex)); }
  };
  return (
    <Modal title={`Share "${doc.name}"`} onClose={onClose}>
      <form onSubmit={submit} className="stack">
        <Banner result={err} />
        <label>Share with<input list="emails" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="user@tenant-b.com" /></label>
        <datalist id="emails">{DEMO_EMAILS.map((m) => <option key={m} value={m} />)}</datalist>
        <label>Permission
          <select value={perm} onChange={(e) => setPerm(e.target.value)}>
            <option value="DOWNLOAD">DOWNLOAD — view and decrypt</option>
            <option value="VIEW">VIEW — metadata only</option>
          </select>
        </label>
        <button className="primary" disabled={!email}>Share</button>
      </form>
    </Modal>
  );
}
