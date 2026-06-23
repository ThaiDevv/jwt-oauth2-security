import { useState } from 'react'

export default function RegisterPage({ apiBase, server }) {
  const [form, setForm]     = useState({ username: '', password: '', role: 'user' })
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState(null)

  const handleRegister = async (e) => {
    e.preventDefault()
    setLoading(true); setResult(null)
    try {
      const res = await fetch(`${apiBase}/api/auth/register`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(form),
      })
      const data = await res.json()
      setResult({ ok: res.ok, data })
    } catch (err) {
      setResult({ ok: false, data: { error: err.message } })
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{ maxWidth: 420, margin: '0 auto' }}>
      <div className="card">
        <h1 style={{ fontSize: 24, fontWeight: 800, marginBottom: 6 }}>Đăng ký</h1>
        <p style={{ color: '#64748b', marginBottom: 24, fontSize: 14 }}>
          Server: <strong style={{ color: server === 'secure' ? '#16a34a' : '#dc2626' }}>
            {server === 'secure' ? 'Secure (4001)' : 'Vulnerable (4002)'}
          </strong>
        </p>

        {result && (
          <div className={`alert ${result.ok ? 'alert-success' : 'alert-error'}`}>
            {result.ok ? 'Đăng ký thành công!' : `${result.data.error}`}
          </div>
        )}

        <form onSubmit={handleRegister}>
          <div className="form-group">
            <label className="form-label">Username</label>
            <input className="form-input" value={form.username}
              onChange={e => setForm({...form, username: e.target.value})} required />
          </div>
          <div className="form-group">
            <label className="form-label">Password</label>
            <input className="form-input" type="password" value={form.password}
              onChange={e => setForm({...form, password: e.target.value})} required />
          </div>
          <div className="form-group">
            <label className="form-label">Role</label>
            <select className="form-select" value={form.role}
              onChange={e => setForm({...form, role: e.target.value})}>
              <option value="user">user</option>
              <option value="admin">admin</option>
              <option value="moderator">moderator</option>
            </select>
          </div>
          <button className="btn btn-primary" style={{ width: '100%', justifyContent: 'center', padding: '12px' }}
            type="submit" disabled={loading}>
            {loading ? <><div className="spinner" />&nbsp;Đang đăng ký...</> : 'Đăng ký'}
          </button>
        </form>

        {result?.ok && (
          <div style={{ marginTop: 16, padding: 12, background: '#1e293b', borderRadius: 8 }}>
            <pre style={{ color: '#e2e8f0', fontSize: 12, whiteSpace: 'pre-wrap' }}>
              {JSON.stringify(result.data, null, 2)}
            </pre>
          </div>
        )}
      </div>
    </div>
  )
}
