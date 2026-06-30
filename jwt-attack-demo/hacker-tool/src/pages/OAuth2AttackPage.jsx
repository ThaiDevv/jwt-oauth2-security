import { useState, useEffect, useRef } from 'react'

const OAUTH_SERVER = 'http://localhost:4003'
const CLIENT_ID = 'react-client-app'
const VICTIM_CALLBACK = 'http://localhost:3000/callback'

export default function OAuth2AttackPage() {
  // --- Bước 1: Attacker tạo auth URL của chính mình ---
  const [attackerEmail, setAttackerEmail] = useState('attacker@gmail.com')
  const [attackerPass, setAttackerPass] = useState('attacker123')
  const [attackerState, setAttackerState] = useState('')
  const [attackerAuthUrl, setAttackerAuthUrl] = useState('')

  // --- Bước 2: Craft URL gửi cho Victim ---
  const [craftedUrl, setCraftedUrl] = useState('')
  const [includeState, setIncludeState] = useState(false) // mặc định KHÔNG có state (vulnerable)

  // --- Bước 3: Victim click → callback về client ---
  const [victimCode, setVictimCode] = useState('')
  const [victimState, setVictimState] = useState('')
  const [attackResult, setAttackResult] = useState(null) // { success, message }

  // --- Bước 4: Giải thích Fix ---
  const [showFix, setShowFix] = useState(false)

  const [logs, setLogs] = useState([])
  const logRef = useRef(null)

  const addLog = (msg, type = 'info') => {
    const t = new Date().toLocaleTimeString()
    setLogs(l => [...l, { msg, type, t, id: Math.random() }])
  }

  useEffect(() => {
    if (logRef.current) logRef.current.scrollTop = logRef.current.scrollHeight
  }, [logs])

  // Lắng nghe callback từ popup của Attacker (để lấy state được tạo)
  useEffect(() => {
    const handleMessage = (event) => {
      if (event.data?.type === 'OAUTH_CODE_INTERCEPTED') {
        const { code, state } = event.data
        setVictimCode(code)
        setVictimState(state || '')
        addLog(`[VICTIM CALLBACK] code=${code}`, 'warning')
        addLog(`[VICTIM CALLBACK] state="${state || '(trống)'}" — ${state ? 'có state' : 'KHÔNG có state!'}`, state ? 'info' : 'error')

        // Kiểm tra: nếu server không validate state → tấn công thành công
        // Simulate: client (victim's browser) không check state (vulnerable)
        const savedState = sessionStorage.getItem('victim_oauth_state') || ''
        if (!savedState) {
          setAttackResult({ success: true, reason: 'Server không yêu cầu state — Victim\'s session nhận code của Attacker!' })
          addLog(`✅ TẤN CÔNG THÀNH CÔNG! state không được validate. Victim đang dùng session bind với tài khoản ATTACKER!`, 'success')
        } else if (state !== savedState) {
          setAttackResult({ success: false, reason: `State không khớp (expected="${savedState}", got="${state || '(trống)'}") — Server từ chối!` })
          addLog(`🛡️ Tấn công THẤT BẠI. State="${state}" không khớp với session state="${savedState}".`, 'error')
        } else {
          setAttackResult({ success: true, reason: 'State khớp — code được chấp nhận (đây là flow bình thường, không phải tấn công).' })
          addLog(`ℹ️ State khớp — flow bình thường.`, 'info')
        }
      }
    }
    window.addEventListener('message', handleMessage)
    return () => window.removeEventListener('message', handleMessage)
  }, [])

  // BƯỚC 1: Attacker khởi tạo auth URL (với state ngẫu nhiên của mình)
  const step1_CreateAttackerUrl = () => {
    const st = Math.random().toString(36).substring(2, 10)
    setAttackerState(st)
    const url = `${OAUTH_SERVER}/authorize?client_id=${CLIENT_ID}&redirect_uri=${encodeURIComponent(VICTIM_CALLBACK)}&scope=openid+profile+email&state=${st}`
    setAttackerAuthUrl(url)
    addLog(`[BƯỚC 1] Attacker tạo Authorization URL với state="${st}"`, 'info')
    addLog(`URL: ${url}`, 'warning')
    addLog(`Đây là URL của ATTACKER (dùng account ${attackerEmail})`, 'info')
  }

  // BƯỚC 2: Craft URL gửi cho Victim (xóa state nếu muốn)
  const step2_CraftVictimUrl = () => {
    if (!attackerAuthUrl) {
      addLog(`❌ Cần thực hiện Bước 1 trước!`, 'error')
      return
    }
    let url = attackerAuthUrl
    if (!includeState) {
      // XÓA state khỏi URL
      url = url.replace(/&state=[^&]+/, '').replace(/\?state=[^&]+&?/, '?').replace(/&?state=[^&]+/, '')
      addLog(`[BƯỚC 2] CRAFT URL: Đã XÓA state parameter khỏi URL`, 'error')
    } else {
      addLog(`[BƯỚC 2] Giữ nguyên state (để test case state không khớp)`, 'warning')
    }
    setCraftedUrl(url)
    addLog(`Crafted URL gửi cho Victim: ${url}`, 'warning')
    addLog(`Attacker sẽ gửi URL này cho Victim qua email / tin nhắn`, 'info')
  }

  // BƯỚC 3: Mô phỏng Victim click URL
  const step3_VictimClickUrl = () => {
    if (!craftedUrl) {
      addLog(`❌ Cần thực hiện Bước 2 trước!`, 'error')
      return
    }

    // Giả lập: victim's browser LÀ vulnerable — KHÔNG lưu state vào session
    sessionStorage.removeItem('victim_oauth_state')

    setVictimCode('')
    setVictimState('')
    setAttackResult(null)
    addLog(`[BƯỚC 3] Victim click URL → Truy cập OAuth Server với URL của Attacker`, 'warning')
    addLog(`Victim đăng nhập bằng tài khoản CỦA MÌNH (VD: victim@gmail.com / victim123)`, 'info')
    addLog(`⏳ Mở popup OAuth Server... Đăng nhập bằng email KHÁC với Attacker!`, 'info')

    // Mở popup — victim đăng nhập → redirect về callback của client
    window.open(craftedUrl, 'csrf_victim', 'width=500,height=620,left=200,top=100')
  }

  // TEST SECURE: Victim's browser lưu state trước khi redirect
  const step3_SecureVictimClick = () => {
    if (!attackerAuthUrl) {
      addLog(`❌ Cần thực hiện Bước 1 trước!`, 'error')
      return
    }

    // Secure: victim tự tạo state của mình và lưu vào session
    const victimOwnState = Math.random().toString(36).substring(2, 10)
    sessionStorage.setItem('victim_oauth_state', victimOwnState)

    // Tạo auth URL với state của victim (không phải attacker)
    const secureUrl = `${OAUTH_SERVER}/authorize?client_id=${CLIENT_ID}&redirect_uri=${encodeURIComponent(VICTIM_CALLBACK)}&scope=openid+profile+email&state=${victimOwnState}`

    setVictimCode('')
    setVictimState('')
    setAttackResult(null)
    addLog(`[SECURE MODE] Victim tạo state="${victimOwnState}" và lưu vào session`, 'success')
    addLog(`[SECURE MODE] Redirect với URL có state của victim (KHÔNG phải crafted URL của attacker)`, 'success')
    addLog(`⏳ Mở popup... Đây là flow SECURE — tấn công sẽ thất bại`, 'info')
    window.open(secureUrl, 'csrf_secure', 'width=500,height=620,left=200,top=100')
  }

  const reset = () => {
    setAttackerState('')
    setAttackerAuthUrl('')
    setCraftedUrl('')
    setVictimCode('')
    setVictimState('')
    setAttackResult(null)
    setLogs([])
    sessionStorage.removeItem('victim_oauth_state')
  }

  return (
    <div>
      {/* Header */}
      <div style={{ marginBottom: 20 }}>
        <h1 style={{ fontSize: 22, fontWeight: 800, color: '#f59e0b' }}>
          OAuth2 CSRF Attack — Thiếu validate <code style={{ fontSize: 18, color: '#ef4444' }}>state</code> parameter
        </h1>
        <p style={{ color: '#94a3b8', fontSize: 13, marginTop: 4 }}>
          Mô phỏng tấn công CSRF khi OAuth2 Client không validate tham số <code style={{ color: '#f59e0b' }}>state</code>.
          Attacker tạo auth URL của mình → Gửi cho Victim → Victim đăng nhập → Account của Attacker bị bind vào session của Victim.
        </p>
      </div>

      <div className="grid-2">
        {/* CỘT TRÁI: Các bước tấn công */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>

          {/* BƯỚC 1 */}
          <div className="card">
            <div className="step-header">
              <div className={`step-num ${attackerAuthUrl ? 'done' : ''}`}>1</div>
              <div className="step-title">Attacker tạo Authorization URL</div>
            </div>
            <p style={{ fontSize: 12, color: '#94a3b8', marginBottom: 12 }}>
              Attacker đăng nhập client app → click "Sign in with Google" → Burp Suite bắt Authorization Request.
              URL này dùng <strong style={{ color: '#f59e0b' }}>account của Attacker</strong>, có kèm <code>state</code> ngẫu nhiên.
            </p>
            <div className="form-group" style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
              <div>
                <label className="form-label">Attacker Email (Google account)</label>
                <input className="form-input" value={attackerEmail} onChange={e => setAttackerEmail(e.target.value)} />
              </div>
              <div>
                <label className="form-label">Password</label>
                <input className="form-input" value={attackerPass} onChange={e => setAttackerPass(e.target.value)} />
              </div>
            </div>
            <button className="btn btn-warning" onClick={step1_CreateAttackerUrl} style={{ width: '100%' }}>
              1. Tạo Auth URL (Bắt chước Burp Intercept)
            </button>
            {attackerAuthUrl && (
              <div style={{ marginTop: 10 }}>
                <label className="form-label" style={{ color: '#3b82f6' }}>Authorization URL của Attacker:</label>
                <div className="code-box" style={{ fontSize: 10, wordBreak: 'break-all' }}>
                  {attackerAuthUrl}
                </div>
                <div style={{ marginTop: 6, padding: '6px 10px', background: 'rgba(245,158,11,0.1)', borderRadius: 5, fontSize: 11, color: '#fbbf24' }}>
                  🔑 State của Attacker: <strong>{attackerState}</strong> — Đây là state gắn với SESSION của Attacker, KHÔNG phải Victim
                </div>
              </div>
            )}
          </div>

          {/* BƯỚC 2 */}
          <div className="card">
            <div className="step-header">
              <div className={`step-num ${craftedUrl ? 'done' : ''}`}>2</div>
              <div className="step-title">Craft URL gửi cho Victim</div>
            </div>
            <p style={{ fontSize: 12, color: '#94a3b8', marginBottom: 12 }}>
              Attacker <strong style={{ color: '#ef4444' }}>xóa state</strong> khỏi URL rồi gửi cho Victim qua email / chat.
              Victim không biết URL này thực ra là của Attacker.
            </p>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 12, padding: '8px 12px', background: '#0f1724', borderRadius: 6, border: '1px solid #334155' }}>
              <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, cursor: 'pointer', color: '#e2e8f0' }}>
                <input
                  type="checkbox"
                  checked={includeState}
                  onChange={e => setIncludeState(e.target.checked)}
                  style={{ width: 16, height: 16, accentColor: '#22c55e' }}
                />
                Giữ lại state trong URL (test case state không khớp)
              </label>
              <span style={{ fontSize: 11, color: includeState ? '#22c55e' : '#ef4444' }}>
                {includeState ? '← State sẽ được giữ (nhưng là state của ATTACKER)' : '← Không có state (CSRF vulnerable ✅)'}
              </span>
            </div>
            <button className="btn btn-danger" onClick={step2_CraftVictimUrl} style={{ width: '100%' }} disabled={!attackerAuthUrl}>
              2. Craft URL gửi Victim (Xóa / Giữ state)
            </button>
            {craftedUrl && (
              <div style={{ marginTop: 10 }}>
                <label className="form-label" style={{ color: '#ef4444' }}>🔴 URL sẽ gửi cho Victim:</label>
                <div className="code-box danger" style={{ fontSize: 10, wordBreak: 'break-all', position: 'relative', paddingRight: 55 }}>
                  <button className="copy-btn" style={{ position: 'absolute', right: 5, top: 5, padding: '2px 6px', fontSize: 10 }}
                    onClick={() => { navigator.clipboard.writeText(craftedUrl); alert('Đã copy!') }}>
                    Copy
                  </button>
                  {craftedUrl}
                </div>
                {!includeState && (
                  <div style={{ marginTop: 6, padding: '6px 10px', background: 'rgba(239,68,68,0.1)', borderRadius: 5, fontSize: 11, color: '#f87171' }}>
                    ⚠️ URL này KHÔNG có <code>state</code> — Nếu server không validate → Tấn công thành công!
                  </div>
                )}
              </div>
            )}
          </div>

          {/* BƯỚC 3 */}
          <div className="card">
            <div className="step-header">
              <div className={`step-num ${attackResult ? (attackResult.success ? 'done' : '') : ''}`}>3</div>
              <div className="step-title">Victim click URL → Đăng nhập → Callback</div>
            </div>
            <p style={{ fontSize: 12, color: '#94a3b8', marginBottom: 12 }}>
              Victim nhận URL qua email → click → đăng nhập bằng tài khoản của <strong style={{ color: '#22c55e' }}>VICTIM</strong>.
              OAuth server cấp <code>code</code> (của Attacker's account) → redirect về client.
              Client không validate <code>state</code> → bind session Victim với account Attacker!
            </p>
            <div style={{ display: 'flex', gap: 10 }}>
              <button className="btn btn-danger" onClick={step3_VictimClickUrl} style={{ flex: 1 }} disabled={!craftedUrl}>
                🎣 Giả lập Victim Click (VULNERABLE)
              </button>
              <button className="btn" onClick={step3_SecureVictimClick}
                style={{ flex: 1, background: 'linear-gradient(135deg,#15803d,#166534)', color: '#fff' }}
                disabled={!attackerAuthUrl}>
                🛡️ Test Secure Fix
              </button>
            </div>

            {/* Kết quả tấn công */}
            {attackResult && (
              <div className={`result-panel ${attackResult.success ? 'warning' : 'error'}`} style={{ marginTop: 12 }}>
                <div style={{ fontWeight: 800, fontSize: 14, marginBottom: 6 }}>
                  {attackResult.success ? '💀 TẤN CÔNG THÀNH CÔNG!' : '🛡️ Tấn công thất bại — Hệ thống an toàn!'}
                </div>
                <div style={{ fontSize: 12, color: attackResult.success ? '#fbbf24' : '#86efac' }}>
                  {attackResult.reason}
                </div>
                {victimCode && (
                  <div style={{ marginTop: 8 }}>
                    <span style={{ fontSize: 11, color: '#94a3b8' }}>Auth Code nhận được: </span>
                    <code style={{ fontSize: 11, color: '#22c55e', wordBreak: 'break-all' }}>{victimCode}</code>
                  </div>
                )}
                {victimState !== undefined && (
                  <div style={{ marginTop: 4 }}>
                    <span style={{ fontSize: 11, color: '#94a3b8' }}>State trả về: </span>
                    <code style={{ fontSize: 11, color: victimState ? '#3b82f6' : '#ef4444' }}>
                      {victimState || '(trống — không có state)'}
                    </code>
                  </div>
                )}
              </div>
            )}
          </div>
        </div>

        {/* CỘT PHẢI: Log + Giải thích */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>

          {/* Sơ đồ tấn công */}
          <div className="card">
            <div className="card-title">🧠 Sơ đồ CSRF Attack</div>
            <div style={{ fontSize: 11, color: '#94a3b8', lineHeight: 1.8, fontFamily: 'monospace' }}>
              <div style={{ color: '#3b82f6' }}>1. Attacker tạo Auth URL (dùng account attacker)</div>
              <div style={{ paddingLeft: 16, color: '#64748b' }}>GET /authorize?client_id=...&redirect_uri=...&state=<span style={{ color: '#f59e0b' }}>ATTACKER_STATE</span></div>

              <div style={{ color: '#ef4444', marginTop: 6 }}>2. Attacker XÓA state → gửi URL cho Victim</div>
              <div style={{ paddingLeft: 16, color: '#64748b' }}>GET /authorize?client_id=...&redirect_uri=... <span style={{ color: '#ef4444', textDecoration: 'line-through' }}>&state=ATTACKER_STATE</span></div>

              <div style={{ color: '#22c55e', marginTop: 6 }}>3. Victim click → đăng nhập Google bằng account victim</div>
              <div style={{ paddingLeft: 16, color: '#64748b' }}>OAuth server cấp code → redirect về /callback?code=XYZ&state=</div>

              <div style={{ color: '#ef4444', marginTop: 6 }}>4. Client KHÔNG validate state → Chấp nhận code!</div>
              <div style={{ paddingLeft: 16, color: '#64748b' }}>→ Victim's session bị bind với <span style={{ color: '#ef4444', fontWeight: 700 }}>tài khoản của Attacker</span></div>

              <div style={{ color: '#f59e0b', marginTop: 8, padding: '6px 10px', background: 'rgba(245,158,11,0.08)', borderRadius: 5 }}>
                💡 Kết quả: Victim đang dùng app với account của Attacker!<br/>
                → Attacker biết account của mình → Đọc được dữ liệu của Victim
              </div>
            </div>
          </div>

          {/* Attack Log */}
          <div className="card" style={{ flex: 1 }}>
            <div className="card-title">
              Attack Log
              <button className="btn btn-ghost" style={{ marginLeft: 'auto', padding: '4px 10px', fontSize: 11 }}
                onClick={reset}>Reset</button>
            </div>
            <div className="attack-log" ref={logRef} style={{ height: 200 }}>
              {logs.length === 0 ? (
                <div style={{ color: '#475569', fontStyle: 'italic', padding: 8 }}>Chưa có log...</div>
              ) : (
                logs.map(l => (
                  <div key={l.id} className={`log-entry ${l.type}`}>
                    <span style={{ color: '#475569' }}>[{l.t}]</span> {l.msg}
                  </div>
                ))
              )}
            </div>
          </div>

          {/* Cách fix */}
          <div className="card">
            <div className="card-title" style={{ cursor: 'pointer' }} onClick={() => setShowFix(f => !f)}>
              🛡️ Cách Khắc Phục (RFC 6749)
              <span style={{ marginLeft: 'auto', fontSize: 12, color: '#64748b' }}>{showFix ? '▲ Ẩn' : '▼ Xem'}</span>
            </div>
            {showFix && (
              <div style={{ fontSize: 12, color: '#94a3b8', lineHeight: 1.7 }}>
                <div style={{ marginBottom: 8, color: '#22c55e', fontWeight: 700 }}>Client phải làm:</div>
                <div className="code-box" style={{ fontSize: 11 }}>
{`// 1. Tạo state ngẫu nhiên trước khi redirect
const state = crypto.randomUUID()
sessionStorage.setItem('oauth_state', state)

// 2. Gắn state vào authorization URL
window.location.href = \`/authorize?...&state=\${state}\`

// 3. Tại callback: KIỂM TRA state
const returnedState = params.get('state')
const savedState = sessionStorage.getItem('oauth_state')
if (returnedState !== savedState) {
  throw new Error('CSRF detected! State mismatch.')
}`}
                </div>
                <div style={{ marginTop: 10, color: '#f59e0b', fontWeight: 700 }}>Tại sao lại fix được?</div>
                <div style={{ marginTop: 4 }}>
                  State được lưu trong <code>sessionStorage</code> của Victim's browser.
                  Attacker không thể biết giá trị này (khác origin).
                  Khi callback về với state trống / sai → client từ chối → Tấn công thất bại. ✅
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
