import { BrowserRouter, Routes, Route, Link, useLocation } from 'react-router-dom'
import { useState, useEffect } from 'react'
import './index.css'
import LoginPage    from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import ProfilePage  from './pages/ProfilePage'

function Navbar({ server, setServer }) {
  const loc = useLocation()
  const isLoggedIn = !!localStorage.getItem('jwt_token')
  return (
    <nav className="navbar">
      <div className="navbar-brand">JWT Security Demo</div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
        <div style={{ display: 'flex', gap: 6 }}>
          <button
            className={`toggle-btn ${server === 'secure' ? 'active-secure' : ''}`}
            onClick={() => !isLoggedIn && setServer('secure')}
            disabled={isLoggedIn}
            style={isLoggedIn ? { opacity: 0.5, cursor: 'not-allowed' } : {}}
            title={isLoggedIn ? "Đăng xuất để thay đổi chế độ server" : "Port 4001 — RS256 only, protected"}
          >Secure (4001)</button>
          <button
            className={`toggle-btn ${server === 'vulnerable' ? 'active-vulnerable' : ''}`}
            onClick={() => !isLoggedIn && setServer('vulnerable')}
            disabled={isLoggedIn}
            style={isLoggedIn ? { opacity: 0.5, cursor: 'not-allowed' } : {}}
            title={isLoggedIn ? "Đăng xuất để thay đổi chế độ server" : "Port 4002 — CVE-2015-9235 vulnerable"}
          >Vulnerable (4002)</button>
        </div>
        <div className="navbar-links">
          {!isLoggedIn && (
            <>
              <Link to="/login">    <button className={`nav-btn ${loc.pathname==='/login'||loc.pathname==='/'?'active':''}`}>Login</button></Link>
              <Link to="/register"><button className={`nav-btn ${loc.pathname==='/register'?'active':''}`}>Register</button></Link>
            </>
          )}
          <Link to="/profile"> <button className={`nav-btn ${loc.pathname==='/profile'?'active':''}`}>Profile</button></Link>
        </div>
      </div>
    </nav>
  )
}

export default function App() {
  const [server, setServer] = useState(() => {
    return localStorage.getItem('jwt_server') || 'vulnerable'
  })

  const apiBase = server === 'secure' ? 'http://localhost:4001' : 'http://localhost:4002'

  useEffect(() => {
    localStorage.setItem('jwt_server', server)
  }, [server])

  return (
    <BrowserRouter>
      <Navbar server={server} setServer={setServer} />
      <div className="container" style={{ marginTop: 28 }}>
        <Routes>
          <Route path="/"         element={<LoginPage    apiBase={apiBase} server={server} />} />
          <Route path="/login"    element={<LoginPage    apiBase={apiBase} server={server} />} />
          <Route path="/register" element={<RegisterPage apiBase={apiBase} server={server} />} />
          <Route path="/profile"  element={<ProfilePage  apiBase={apiBase} server={server} />} />
        </Routes>
      </div>
    </BrowserRouter>
  )
}
