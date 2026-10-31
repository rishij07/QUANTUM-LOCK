import React, { useRef, useState } from 'react';
import { CheckCircle2, FileUp, KeyRound, Lock, HardDrive, Database } from 'lucide-react';
import { api, fmtBytes } from '../api.js';
import { Banner, errResult } from '../components/ui.jsx';

const LEVELS = [
  ['PUBLIC', 'Anyone in your tenant can open it'],
  ['INTERNAL', 'Tenant members can open it'],
  ['CONFIDENTIAL', 'Only owner, admins and explicit shares'],
];

export default function UploadPage() {
  const [file, setFile] = useState(null);
  const [level, setLevel] = useState('INTERNAL');
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState(null);
  const [receipt, setReceipt] = useState(null);
  const [drag, setDrag] = useState(false);
  const input = useRef(null);

  const submit = async () => {
    setBusy(true); setErr(null); setReceipt(null);
    try {
      const form = new FormData();
      form.append('file', file);
      form.append('classification', level);
      setReceipt(await api('/documents', { method: 'POST', form }));
      setFile(null);
    } catch (e) { setErr(errResult(e)); } finally { setBusy(false); }
  };

  return (
    <>
      <header className="pagehead"><div><h1>Upload document</h1><p>The file is encrypted on the server before anything is stored. MinIO only ever receives ciphertext.</p></div></header>
      <Banner result={err} onClose={() => setErr(null)} />
      <div className="two-col">
        <section className="panel">
          <div
            className={`drop ${drag ? 'over' : ''}`}
            onDragOver={(e) => { e.preventDefault(); setDrag(true); }}
            onDragLeave={() => setDrag(false)}
            onDrop={(e) => { e.preventDefault(); setDrag(false); setFile(e.dataTransfer.files[0] || null); }}
            onClick={() => input.current.click()}
            role="button" tabIndex={0}
            onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && input.current.click()}
          >
            <FileUp size={28} />
            {file ? <><strong>{file.name}</strong><span className="sub">{fmtBytes(file.size)}</span></> : <><strong>Drop a file here or click to choose</strong><span className="sub">Up to 25 MB</span></>}
            <input ref={input} type="file" hidden onChange={(e) => setFile(e.target.files[0] || null)} />
          </div>
          <fieldset className="levels">
            <legend>Classification</legend>
            {LEVELS.map(([v, hint]) => (
              <label key={v} className={level === v ? 'on' : ''}>
                <input type="radio" name="lvl" checked={level === v} onChange={() => setLevel(v)} />
                <span><strong>{v}</strong><small>{hint}</small></span>
              </label>
            ))}
          </fieldset>
          <button className="primary wide" disabled={!file || busy} onClick={submit}>{busy ? 'Encrypting...' : 'Encrypt and store'}</button>
        </section>

        <section className="panel">
          <div className="panel-head"><h2>What happens to your file</h2></div>
          <ol className="pipe">
            <li className={receipt ? 'done' : ''}><KeyRound size={18} /><div><strong>Generate a random DEK</strong><span>256-bit key, new for every document version</span></div></li>
            <li className={receipt ? 'done' : ''}><Lock size={18} /><div><strong>Encrypt with AES-256-GCM</strong><span>random IV, authentication tag, bound to document id and version</span></div></li>
            <li className={receipt ? 'done' : ''}><HardDrive size={18} /><div><strong>Store ciphertext in MinIO</strong><span>no plaintext is ever written</span></div></li>
            <li className={receipt ? 'done' : ''}><KeyRound size={18} /><div><strong>Wrap the DEK with the KEK</strong><span>through the KMS abstraction</span></div></li>
            <li className={receipt ? 'done' : ''}><Database size={18} /><div><strong>Save metadata in PostgreSQL</strong><span>name, owner, tenant, version, wrapped DEK</span></div></li>
          </ol>
          {receipt && (
            <div className="receipt">
              <h3><CheckCircle2 size={18} className="t-ok" /> Stored securely</h3>
              <dl className="mono">
                <dt>Document</dt><dd>{receipt.doc.name} (v{receipt.version})</dd>
                <dt>Algorithm</dt><dd>{receipt.algorithm}</dd>
                <dt>Plaintext size</dt><dd>{fmtBytes(receipt.plainBytes)}</dd>
                <dt>Ciphertext size</dt><dd>{fmtBytes(receipt.cipherBytes)} (IV + tag included)</dd>
                <dt>MinIO object</dt><dd className="break">{receipt.objectKey}</dd>
                <dt>Wrapped DEK</dt><dd>{receipt.wrappedDekPreview} <span className="sub">by {receipt.kekId}</span></dd>
              </dl>
            </div>
          )}
        </section>
      </div>
    </>
  );
}
