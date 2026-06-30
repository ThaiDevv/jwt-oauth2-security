import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'

/**
 * OAuth2CallbackPage — Client-side OAuth2 Callback Handler
 *
 * Đây là callback page cho CLIENT-SIDE OAuth2 flow (React app).
 * Server-side OAuth2 flow đã được xử lý tại:
 *   - http://localhost:4002/oauth2/callback (Vulnerable — không validate state)
 *   - http://localhost:4001/oauth2/callback (Secure — validate state từ session)
 *
 * ⚠️ LỖ HỔNG CSRF (Vulnerable mode):
 *   Client lưu state vào localStorage khi bắt đầu OAuth flow.
 *   Tại callback, KHÔNG so sánh state trả về → CSRF thành công!
 *
 * ✅ FIX (Secure mode):
 *   - Dùng server-side /oauth2/callback với HttpSession
 *   - Server tự validate state → client không thể giả mạo
 */
export default function OAuth2CallbackPage({ apiBase, server }) {
  const loc = useLocation()
  const navigate = useNavigate()
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)
  const [csrfWarning, setCsrfWarning] = useState('')
  const [debugInfo, setDebugInfo] = useState(null)

  useEffect(() => {
    const searchParams = new URLSearchParams(loc.search)
    const code = searchParams.get('code')
    const returnedState = searchParams.get('state') || ''

    if (!code) {
      setError('Không tìm thấy Authorization Code trong callback URL.')
      setLoading(false)
      return
    }

    // Lấy state đã lưu từ localStorage
    const savedState = localStorage.getItem('oauth_state') || ''

    // Build debug info để hiển thị
    setDebugInfo({
      code: code.substring(0, 20) + '...',
      returnedState: returnedState || '(trống)',
      savedState: savedState || '(trống — không có trong storage)',
      stateMatch: returnedState === savedState,
      server: server
    })

    // ============================================================
    // Nếu là popup (từ Hacker Tool mô phỏng), gửi message về parent
    // ============================================================
    if (window.opener) {
      window.opener.postMessage({ type: 'OAUTH_CODE_INTERCEPTED', code, state: returnedState }, '*')
      setLoading(false)
      return
    }

    // ============================================================
    // ⚠️ VULNERABLE MODE: Không validate state!
    // Ghi lại cảnh báo nhưng vẫn tiếp tục xử lý code
    // ============================================================
    if (server === 'vulnerable') {
      if (!returnedState) {
        setCsrfWarning(
          `⚠️ CSRF VULN DETECTED!\n` +
          `state trả về = "" (trống)\n` +
          `savedState   = "${savedState}"\n` +
          `→ Server KHÔNG validate → vẫn xử lý code!`
        )
      } else if (returnedState !== savedState) {
        setCsrfWarning(
          `⚠️ CSRF VULN DETECTED!\n` +
          `returnedState = "${returnedState}"\n` +
          `savedState    = "${savedState}"\n` +
          `State KHÔNG KHỚP → nhưng server vẫn chấp nhận!`
        )
      }
    }

    // ============================================================
    // ✅ SECURE MODE: Validate state trước khi gửi lên server
    // (Tầng bảo vệ thứ 2 — tầng 1 là server-side /oauth2/callback)
    // ============================================================
    if (server === 'secure') {
      if (!returnedState || returnedState !== savedState) {
        setError(
          `Invalid OAuth State!\n` +
          `returnedState = "${returnedState || '(trống)'}"\n` +
          `savedState    = "${savedState || '(trống)'}"\n` +
          `→ CSRF Attack phát hiện! Từ chối xử lý.`
        )
        setLoading(false)
        return
      }
    }

    // ============================================================
    // Gửi code lên server để đổi lấy JWT token
    // ============================================================
    const exchangeCode = async () => {
      try {
        const res = await fetch(`${apiBase}/api/auth/oauth2-login`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ code, state: returnedState })
        })

        const data = await res.json()
        if (!res.ok) {
          throw new Error(data.error || 'Đổi mã code thất bại')
        }

        localStorage.setItem('jwt_token', data.token)
        localStorage.setItem('jwt_publicKey', data.publicKey)
        localStorage.setItem('jwt_user', JSON.stringify(data.user))
        localStorage.setItem('jwt_server', server)
        localStorage.removeItem('oauth_state') // Xóa state sau khi dùng

        navigate('/profile')
      } catch (err) {
        setError(err.message)
      } finally {
        setLoading(false)
      }
    }

    exchangeCode()
  }, [loc, apiBase, server, navigate])

  return (
    <div style={{ maxWidth: 520, margin: '40px auto' }}>
      <div className="card" style={{ textAlign: 'center', padding: '40px 24px' }}>
        {loading && (
          <div>
            <div className="spinner" style={{ width: 40, height: 40, margin: '0 auto 20px' }} />
            <h2 style={{ fontSize: 18, fontWeight: 700 }}>Đang xác thực OAuth2...</h2>
            <p style={{ color: '#64748b', fontSize: 14, marginTop: 8 }}>
              Hệ thống đang trao đổi mã code với Resource Server
            </p>
          </div>
        )}

        {/* CSRF Warning — chỉ xuất hiện ở Vulnerable mode */}
        {csrfWarning && !error && (
          <div style={{
            marginBottom: 16, padding: 14, background: 'rgba(239,68,68,0.1)',
            borderRadius: 8, fontSize: 12, color: '#f87171', textAlign: 'left',
            fontFamily: 'monospace', border: '1px solid rgba(239,68,68,0.3)',
            whiteSpace: 'pre-line', lineHeight: 1.8
          }}>
            {csrfWarning}
          </div>
        )}

        {/* Debug Info */}
        {debugInfo && !loading && !error && (
          <div style={{
            marginBottom: 16, padding: 14, background: 'rgba(51,65,85,0.5)',
            borderRadius: 8, fontSize: 11, color: '#94a3b8', textAlign: 'left',
            fontFamily: 'monospace', lineHeight: 1.8
          }}>
            <div style={{ fontWeight: 700, marginBottom: 6, color: '#cbd5e1' }}>OAuth2 Debug Info</div>
            <div>code         = {debugInfo.code}</div>
            <div>returnState  = {debugInfo.returnedState}</div>
            <div>savedState   = {debugInfo.savedState}</div>
            <div>stateMatch   = {debugInfo.stateMatch ? '✅ MATCH' : '❌ MISMATCH'}</div>
            <div>server       = {debugInfo.server}</div>
          </div>
        )}

        {/* Error */}
        {error && (
          <div>
            <div style={{ fontSize: 50, marginBottom: 16 }}>
              {server === 'secure' ? '🛡️' : '❌'}
            </div>
            <h2 style={{
              fontSize: 18, fontWeight: 700,
              color: server === 'secure' ? '#22c55e' : '#ef4444'
            }}>
              {server === 'secure' ? 'CSRF Attack Bị Chặn!' : 'Xác thực thất bại'}
            </h2>
            <pre style={{
              color: server === 'secure' ? '#86efac' : '#f87171',
              fontSize: 12,
              background: server === 'secure' ? 'rgba(34,197,94,0.1)' : 'rgba(239,68,68,0.1)',
              padding: 14, borderRadius: 8, margin: '16px 0',
              textAlign: 'left', whiteSpace: 'pre-wrap', lineHeight: 1.8
            }}>
              {error}
            </pre>
            <button className="btn btn-primary" onClick={() => navigate('/login')}>
              Quay lại đăng nhập
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
