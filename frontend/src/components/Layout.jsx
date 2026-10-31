import React from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { LayoutDashboard, Files, Upload, Share2, ScrollText, Swords, LogOut, Lock } from 'lucide-react';
import { useAuth } from '../auth.jsx';

const NAV = [
  ['/', 'Dashboard', LayoutDashboard],
  ['/documents', 'Documents', Files],
  ['/upload', 'Upload', Upload],
  ['/sharing', 'Sharing & Access', Share2],
  ['/audit', 'Audit Logs', ScrollText],
  ['/attacks', 'Attack Simulation', Swords],
];

export default function Layout() {
  const { user, logout } = useAuth();
  const nav = useNavigate();
  return (
    <div className="shell">
      <aside className="side">
        <div className="brand"><Lock size={20} /><span>QUANTUM-LOCK</span></div>
        <nav>
          {NAV.map(([to, label, Icon]) => (
            <NavLink key={to} to={to} end={to === '/'} className={({ isActive }) => (isActive ? 'active' : '')}>
              <Icon size={18} /><span>{label}</span>
            </NavLink>
          ))}
        </nav>
        <div className="whoami">
          <div className="tenant-pill">{user.tenantName}</div>
          <div className="who-name">{user.displayName}</div>
          <div className="who-mail mono">{user.email}</div>
          <div className="who-role">{user.role === 'ADMIN' ? 'Tenant admin' : 'Employee'}</div>
          <button className="ghost" onClick={() => { logout(); nav('/login'); }}><LogOut size={16} /> Sign out</button>
        </div>
      </aside>
      <main className="main"><Outlet /></main>
    </div>
  );
}
