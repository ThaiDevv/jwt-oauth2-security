import { useState } from 'react'
import { useNavigate } from 'react-router-dom'

export default function LoginPage({ apiBase, server }) {
  const [form, setForm]     = useState({ username: '', password: '' })
  const [loading, setLoading] = useState(false)
  const [error, setError]   = useState('')
  const navigate = useNavigate()

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
