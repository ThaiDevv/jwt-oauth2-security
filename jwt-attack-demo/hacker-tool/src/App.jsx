import { BrowserRouter, Routes, Route, useLocation, useNavigate } from 'react-router-dom'
import NoAuthAttackPage from './pages/NoAuthAttackPage'
import JwtAttackPage  from './pages/JwtAttackPage'
import CallbackPage   from './pages/CallbackPage'
import './index.css'

function Sidebar() {
  const loc = useLocation()
  const nav = useNavigate()
  const items = [
    { path: '/noauth', label: 'No-Auth Attack' },
    { path: '/',       label: 'JWT Attack' },
  ]
  return (
    <div className="sidebar">
      <div className="sidebar-brand">
        Hacker Tool<br/>
        <span style={{ fontSize: 11, color: '#64748b', fontWeight: 400 }}>Security Demo Lab</span>
      </div>
      <nav className="sidebar-nav">
        {items.map(i => (
          <div key={i.path} className={`nav-item ${loc.pathname === i.path ? 'active' : ''}`}
               onClick={() => nav(i.path)}>
            {i.label}
          </div>
        ))}
      </nav>
      <div style={{ padding: '16px 20px', fontSize: 11, color: '#475569', borderTop: '1px solid #334155' }}>
        <div style={{ color: '#ef4444' }}>No-Auth: http://localhost:4000</div>
        <div style={{ color: '#22c55e' }}>Secure: http://localhost:4001</div>
        <div style={{ color: '#f59e0b' }}>Vulnerable: http://localhost:4002</div>
        <div style={{ color: '#a855f7' }}>OAuth2: http://localhost:4003</div>
      </div>
    </div>
  )
}

function Layout({ children }) {
  return (
    <div className="layout">
      <Sidebar />
      <main className="main">{children}</main>
    </div>
  )
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/callback" element={<CallbackPage />} />
        <Route path="/*" element={
          <Layout>
            <Routes>
              <Route path="/noauth" element={<NoAuthAttackPage />} />
              <Route path="/"       element={<JwtAttackPage />} />
            </Routes>
          </Layout>
        } />
      </Routes>
    </BrowserRouter>
  )
}
