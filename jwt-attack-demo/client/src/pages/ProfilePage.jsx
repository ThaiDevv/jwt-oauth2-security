import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'

function decodeJwt(token) {
  try {
    const [h, p] = token.split('.')
    return {
      header:  JSON.parse(atob(h.replace(/-/g,'+').replace(/_/g,'/'))),
      payload: JSON.parse(atob(p.replace(/-/g,'+').replace(/_/g,'/'))),
    }
  } catch { return null }
}

function CopyButton({ text }) {
  const [copied, setCopied] = useState(false)
  const copy = () => {
    navigator.clipboard.writeText(text).then(() => { setCopied(true); setTimeout(() => setCopied(false), 1500) })
  }
  return <button className="copy-btn" onClick={copy}>{copied ? 'Copied' : 'Copy'}</button>
}

function CountDown({ exp }) {
  const [remain, setRemain] = useState('')
  useEffect(() => {
    const tick = () => {
      const left = exp * 1000 - Date.now()
      if (left <= 0) { setRemain('Expired'); return }
      const m = String(Math.floor(left/60000)).padStart(2,'0')
      const s = String(Math.floor((left%60000)/1000)).padStart(2,'0')
      setRemain(`${m}:${s}`)
    }
    tick()
    const id = setInterval(tick, 1000)
    return () => clearInterval(id)
  }, [exp])
  return <span className="countdown">Expires in: <strong>{remain}</strong></span>
}

export default function ProfilePage({ apiBase, server }) {
  const token     = localStorage.getItem('jwt_token')
  const publicKey = localStorage.getItem('jwt_publicKey')
  const user      = JSON.parse(localStorage.getItem('jwt_user') || 'null')
  const navigate  = useNavigate()

  const [profileRes, setProfileRes] = useState(null)
  const [adminRes,   setAdminRes]   = useState(null)
  const [loading,    setLoading]    = useState({})

  if (!token) return (
    <div className="card" style={{ maxWidth: 500, margin: '0 auto', textAlign: 'center' }}>
      <p style={{ color: '#64748b' }}>Bạn chưa đăng nhập.</p>
      <button className="btn btn-primary" style={{ marginTop: 16 }} onClick={() => navigate('/login')}>Đăng nhập</button>
    </div>
  )

  const decoded = decodeJwt(token)
  const role = user?.role || decoded?.payload?.role

  const callApi = async (endpoint, key) => {
    setLoading(l => ({...l, [key]: true}))
    try {
      const res = await fetch(`${apiBase}${endpoint}`, {
        headers: { Authorization: `Bearer ${token}` }
      })
      const data = await res.json()
      if (key === 'profile') setProfileRes({ status: res.status, data })
      else setAdminRes({ status: res.status, data })
    } catch (e) {
      const err = { error: e.message }
      if (key === 'profile') setProfileRes({ status: 0, data: err })
      else setAdminRes({ status: 0, data: err })
    } finally {
      setLoading(l => ({...l, [key]: false}))
    }
  }

  return (
    <div>
      {/* Header card */}
      <div className="card" style={{ display: 'flex', alignItems: 'center', gap: 20, flexWrap: 'wrap' }}>
        <div style={{
          width: 64, height: 64, borderRadius: '50%', background: 'linear-gradient(135deg,#2563eb,#7c3aed)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          color: 'white', fontSize: 24, fontWeight: 800, flexShrink: 0
        }}>
          {(user?.username || 'U')[0].toUpperCase()}
        </div>
        <div style={{ flex: 1 }}>
          <div style={{ fontSize: 20, fontWeight: 700 }}>{user?.username}</div>
          <div style={{ marginTop: 4, display: 'flex', gap: 8, alignItems: 'center' }}>
            <span className={`badge badge-${role}`}>{role}</span>
            <span style={{ fontSize: 12, color: '#64748b' }}>
              Server: <strong style={{ color: server==='secure'?'#16a34a':'#dc2626' }}>
                {server==='secure'?'Secure (4001)':'Vulnerable (4002)'}
              </strong>
            </span>
          </div>
          {decoded?.payload?.exp && <div style={{ marginTop: 6, fontSize: 13, color: '#64748b' }}>
            <CountDown exp={decoded.payload.exp} />
          </div>}
        </div>
        <button className="btn btn-outline btn-sm" onClick={() => { localStorage.clear(); navigate('/login') }}>
          Logout
        </button>
      </div>

      <div className="grid-2">
        {/* JWT Token */}
        <div className="card">
          <div className="card-title">JWT Token</div>
          <div className="code-box" style={{ fontSize: 11 }}>
            <CopyButton text={token} />
            {token}
          </div>
          {decoded && (
            <div style={{ marginTop: 16 }}>
              <div className="jwt-section">
                <div className="jwt-section-label">Header</div>
                <div className="code-box jwt-header" style={{ maxHeight: 80 }}>
                  {JSON.stringify(decoded.header, null, 2)}
                </div>
              </div>
              <div className="jwt-section">
                <div className="jwt-section-label">Payload</div>
                <div className="code-box jwt-payload" style={{ maxHeight: 120 }}>
                  {JSON.stringify(decoded.payload, null, 2)}
                </div>
              </div>
            </div>
          )}
        </div>

        {/* Public Key */}
        <div className="card">
          <div className="card-title">RSA Public Key</div>
          <div className="code-box" style={{ fontSize: 10 }}>
            <CopyButton text={publicKey} />
            {publicKey}
          </div>
          <div className="alert alert-info" style={{ marginTop: 12, fontSize: 12 }}>
            Public key này được server cấp. Hacker Tool có thể dùng key này để tấn công CVE-2015-9235.
          </div>
        </div>
      </div>

      {/* API Test Buttons */}
      <div className="card">
        <div className="card-title">Kiểm tra API</div>
        <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap', marginBottom: 16 }}>
          <button className="btn btn-primary" onClick={() => callApi('/api/profile','profile')} disabled={loading.profile}>
            {loading.profile ? <><div className="spinner"/>&nbsp;</> : ''} Test /api/profile
          </button>
          <button className="btn btn-danger" onClick={() => callApi('/api/admin','admin')} disabled={loading.admin}>
            {loading.admin ? <><div className="spinner"/>&nbsp;</> : ''} Test /api/admin
          </button>
          <a href="http://localhost:3001" target="_blank" rel="noopener noreferrer">
            <button className="btn btn-outline">Mở Hacker Tool →</button>
          </a>
        </div>

        {profileRes && (
          <div style={{ marginBottom: 12 }}>
            <div className={`alert ${profileRes.status===200?'alert-success':'alert-error'}`} style={{ marginBottom: 8 }}>
              <strong>GET /api/profile → HTTP {profileRes.status}</strong>
            </div>
            <div className="code-box">{JSON.stringify(profileRes.data, null, 2)}</div>
          </div>
        )}
        {adminRes && (
          <div>
            <div className={`alert ${adminRes.status===200?'alert-success':'alert-error'}`} style={{ marginBottom: 8 }}>
              <strong>GET /api/admin → HTTP {adminRes.status} {adminRes.status===200?'GRANTED':'FORBIDDEN'}</strong>
            </div>
            <div className="code-box">{JSON.stringify(adminRes.data, null, 2)}</div>
          </div>
        )}
      </div>
    </div>
  )
}
