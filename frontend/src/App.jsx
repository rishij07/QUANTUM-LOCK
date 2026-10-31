import React from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './auth.jsx';
import Layout from './components/Layout.jsx';
import Login from './pages/Login.jsx';
import Dashboard from './pages/Dashboard.jsx';
import Documents from './pages/Documents.jsx';
import UploadPage from './pages/Upload.jsx';
import Sharing from './pages/Sharing.jsx';
import Audit from './pages/Audit.jsx';
import Attacks from './pages/Attacks.jsx';

function Guard({ children }) {
  const { user, ready } = useAuth();
  if (!ready) return <div className="boot">Loading vault...</div>;
  return user ? children : <Navigate to="/login" replace />;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route element={<Guard><Layout /></Guard>}>
        <Route path="/" element={<Dashboard />} />
        <Route path="/documents" element={<Documents />} />
        <Route path="/upload" element={<UploadPage />} />
        <Route path="/sharing" element={<Sharing />} />
        <Route path="/audit" element={<Audit />} />
        <Route path="/attacks" element={<Attacks />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
