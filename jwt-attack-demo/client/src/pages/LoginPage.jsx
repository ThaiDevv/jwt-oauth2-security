import { useState } from 'react'
import { useNavigate } from 'react-router-dom'

export default function LoginPage({ apiBase, server }) {
  const [form, setForm]     = useState({ username: '', password: '' })
  const [loading, setLoading] = useState(false)
  const [error, setError]   = useState('')
  const navigate = useNavigate()

  const serverPort = server === 'secure' ? 4001 : 4002
  const serverSideOAuth2Url = `http://localhost:${serverPort}/oauth2/login`

  const handleLogin = async (e) => {
    e.preventDefault()
    setLoading(true); setError('')
    try {
      const res = await fetch(`${apiBase}/api/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(form),
      })
      const data = await res.json()
      if (!res.ok) throw new Error(data.error || 'Đăng nhập thất bại')
      localStorage.setItem('jwt_token',    data.token)
      localStorage.setItem('jwt_publicKey', data.publicKey)
      localStorage.setItem('jwt_user',      JSON.stringify(data.user))
      localStorage.setItem('jwt_server',    server)
      navigate('/profile')
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{ maxWidth: 420, margin: '0 auto' }}>
      <div className="card">
        <h1 style={{ fontSize: 26, fontWeight: 800, textAlign: 'center', marginBottom: 6 }}>Đăng nhập</h1>
        <p style={{ textAlign: 'center', color: '#64748b', marginBottom: 24, fontSize: 14 }}>
          Server: <strong style={{ color: server === 'secure' ? '#16a34a' : '#dc2626' }}>
            {server === 'secure' ? 'Secure (4001)' : 'Vulnerable (4002)'}
          </strong>
        </p>

        {error && <div className="alert alert-error">{error}</div>}

        <form onSubmit={handleLogin}>
          <div className="form-group">
            <label className="form-label">Username</label>
            <input className="form-input" placeholder="admin, alice, bob" value={form.username}
              onChange={e => setForm({...form, username: e.target.value})} required />
          </div>
          <div className="form-group">
            <label className="form-label">Password</label>
            <input className="form-input" type="password" placeholder="admin123, alice123, bob123"
              value={form.password} onChange={e => setForm({...form, password: e.target.value})} required />
          </div>
          <button className="btn btn-primary" style={{ width: '100%', justifyContent: 'center', padding: '12px' }}
            type="submit" disabled={loading}>
            {loading ? <><div className="spinner" />&nbsp;Đang đăng nhập...</> : 'Đăng nhập'}
          </button>
        </form>

        <a href={serverSideOAuth2Url} style={{ textDecoration: 'none', display: 'block', marginTop: 12 }}>
          <button
            className="btn"
            style={{
              width: '100%', justifyContent: 'center', padding: '12px',
              borderColor: '#dadce0', color: '#3c4043', fontWeight: 600,
              background: '#fff', display: 'flex', alignItems: 'center', gap: 10,
              boxShadow: '0 1px 3px rgba(0,0,0,0.2)', borderRadius: 6, cursor: 'pointer',
              border: '1px solid #dadce0', fontSize: 14
            }}
          >
            <svg viewBox="0 0 24 24" width="18" height="18">
              <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" fill="#4285F4"/>
              <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/>
              <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l2.85-2.22.81-.63z" fill="#FBBC05"/>
              <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84c.87-2.6 3.3-4.52 6.16-4.52z" fill="#EA4335"/>
            </svg>
            Đăng nhập bằng Google
          </button>
        </a>

        <div style={{ marginTop: 20, padding: 14, background: '#f8fafc', borderRadius: 8, fontSize: 13 }}>
          <strong>Tài khoản demo:</strong>
          <div style={{ marginTop: 6, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            {[['admin','admin123','admin'],['alice','alice123','user'],['bob','bob123','moderator']].map(([u,p,r]) => (
              <button key={u} className="btn btn-outline btn-sm" onClick={() => setForm({ username: u, password: p })}>
                {u} <span className={`badge badge-${r}`}>{r}</span>
              </button>
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}
