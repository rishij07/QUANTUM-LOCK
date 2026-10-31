import React, { useEffect, useState } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth.jsx';

const DEMO = [
  ['admin@tenant-a.com', 'Tenant A', 'Admin'],
  ['user@tenant-a.com', 'Tenant A', 'Employee'],
  ['user@tenant-b.com', 'Tenant B', 'Employee'],
];
const PASSWORD = 'Quantum#2026';
const PLAIN = 'Everything at rest is ciphertext.';
const GLYPHS = '0123456789abcdef';

/** One orchestrated moment: hex noise resolves into the product promise. */
function CipherLine() {
  const [text, setText] = useState(() => PLAIN.replace(/./g, () => GLYPHS[Math.floor(Math.random() * 16)]));
  useEffect(() => {
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) { setText(PLAIN); return; }
    let frame = 0;
    const id = setInterval(() => {
      frame += 1;
      const settled = Math.floor(frame / 2);
      setText(PLAIN.split('').map((c, i) => (i < settled ? c : c === ' ' ? ' ' : GLYPHS[Math.floor(Math.random() * 16)])).join(''));
      if (settled >= PLAIN.length) clearInterval(id);
    }, 45);
    return () => clearInterval(id);
  }, []);
  return <p className="cipher mono" aria-label={PLAIN}>{text}</p>;
}

export default function Login() {
  const { user, login } = useAuth();
  const nav = useNavigate();
  const [email, setEmail] = useState(DEMO[0][0]);
  const [password, setPassword] = useState(PASSWORD);
  const [err, setErr] = useState(null);
  const [busy, setBusy] = useState(false);

  if (user) return <Navigate to="/" replace />;

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true); setErr(null);
    try { await login(email, password); nav('/'); }
    catch (ex) { setErr(`${ex.status || ''} \u2014 ${ex.message}`); }
    finally { setBusy(false); }
  };

  return (
    <div className="login">
      <section className="login-hero">
        <div className="brand big">QUANTUM-LOCK</div>
        <CipherLine />
        <p className="lede">A zero-trust document vault. Every file gets its own key, every key is wrapped by a master key, every access is checked on the server, and every decision is written to a hash-chained log.</p>
        <ul className="pipeline-mini mono">
          <li>file</li><li>DEK (per version)</li><li>AES-256-GCM</li><li>MinIO ciphertext</li><li>KEK-wrapped DEK</li>
        </ul>
      </section>
      <section className="login-card">
        <h1>Sign in</h1>
        <form onSubmit={submit}>
          <label>Email<input value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="username" /></label>
          <label>Password<input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" /></label>
          {err && <div className="banner blocked"><div><strong>{err}</strong></div></div>}
          <button className="primary" disabled={busy}>{busy ? 'Signing in\u2026' : 'Sign in'}</button>
        </form>
        <div className="demo-users">
          <h2>Demo accounts</h2>
          {DEMO.map(([mail, tenant, role]) => (
            <button key={mail} type="button" className="demo-user" onClick={() => { setEmail(mail); setPassword(PASSWORD); }}>
              <span className="mono">{mail}</span><span>{tenant} · {role}</span>
            </button>
          ))}
          <p className="hint">Password for all demo accounts: <span className="mono">{PASSWORD}</span></p>
        </div>
      </section>
    </div>
  );
}
