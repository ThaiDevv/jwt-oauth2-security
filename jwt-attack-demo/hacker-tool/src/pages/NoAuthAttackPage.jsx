import { useState } from 'react'

function makeLog(msg, type = 'info') {
  const t = new Date().toLocaleTimeString()
  return { msg, type, t, id: Math.random() }
}

export default function NoAuthAttackPage() {
  const [baseUrl, setBaseUrl] = useState('http://localhost:4000')
  const [userId, setUserId] = useState('1')
  const [orderId, setOrderId] = useState('1')
  const [response, setResponse] = useState(null)
  const [loading, setLoading] = useState(false)
  const [logs, setLogs] = useState([makeLog('No-Auth Attack Tool khởi động — v1 Demo', 'info')])

  const addLog = (msg, type) => setLogs(l => [...l.slice(-99), makeLog(msg, type)])

  const attack = async (endpoint, label) => {
    setLoading(true)
    setResponse(null)
    const url = `${baseUrl}${endpoint}`
    addLog(`[ATTACK] ${label}`, 'warning')
    addLog(`GET ${url} — Không cần token, không cần đăng nhập!`, 'info')
    try {
      const res = await fetch(url)
      const data = await res.json()
      setResponse({ status: res.status, url, data })
      if (res.ok) {
        addLog(`✅ TẤN CÔNG THÀNH CÔNG! Status: ${res.status}`, 'error')
        addLog(`Dữ liệu nhạy cảm bị lộ: ${JSON.stringify(data).substring(0, 120)}...`, 'error')
      } else {
        addLog(`Response: ${res.status}`, 'info')
      }
    } catch (e) {
      addLog(`Lỗi kết nối: ${e.message}. Đảm bảo server-no-auth đang chạy (port 4000).`, 'error')
      setResponse({ status: 'ERR', url, data: { error: e.message } })
    } finally {
      setLoading(false)
    }
  }

  const attacks = [
    {
      label: 'Xem Profile User bất kỳ',
      endpoint: () => `/api/users/${userId}/profile`,
      desc: 'GET /api/users/{id}/profile — Không cần đăng nhập. Thay userId để truy cập profile người khác (IDOR).',
    },
    {
      label: 'Xem toàn bộ danh sách User (Admin)',
      endpoint: () => `/api/admin/users`,
      desc: 'GET /api/admin/users — Không kiểm tra role admin. Bất kỳ ai cũng xem được!',
    },
    {
      label: 'Xem Đơn Hàng User bất kỳ',
      endpoint: () => `/api/users/${userId}/orders`,
      desc: 'GET /api/users/{id}/orders — Không kiểm tra ownership. IDOR attack!',
    },
    {
      label: 'Xem Chi Tiết Order (IDOR)',
      endpoint: () => `/api/orders/${orderId}`,
      desc: 'GET /api/orders/{id} — Thay orderId để xem đơn hàng của người khác.',
    },
  ]

  return (
    <div>
      <div style={{ marginBottom: 20 }}>
        <h1 style={{ fontSize: 22, fontWeight: 800, color: '#ef4444' }}>
          No-Auth API Attack
        </h1>
        <p style={{ color: '#94a3b8', fontSize: 13, marginTop: 4 }}>
          v1 — Tấn công API không có bảo mật: Unauthorized Access &amp; IDOR (Insecure Direct Object Reference)
        </p>
      </div>

      <div className="grid-2">
        <div>
          {/* Cấu hình */}
          <div className="card">
            <div className="card-title">⚙️ Cấu Hình Target</div>
            <div className="form-group">
              <label className="form-label">Server URL (server-no-auth Port 4000)</label>
              <input className="form-input" value={baseUrl}
                onChange={e => setBaseUrl(e.target.value)}
                placeholder="http://localhost:4000" />
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
              <div className="form-group">
                <label className="form-label">User ID (thay để IDOR)</label>
                <input className="form-input" type="number" value={userId}
                  onChange={e => setUserId(e.target.value)} min="1" />
              </div>
              <div className="form-group">
                <label className="form-label">Order ID (thay để IDOR)</label>
                <input className="form-input" type="number" value={orderId}
                  onChange={e => setOrderId(e.target.value)} min="1" />
              </div>
            </div>
          </div>

          {/* Tấn công */}
          <div className="card">
            <div className="card-title">🎯 Các Cuộc Tấn Công</div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              {attacks.map((a, i) => (
                <div key={i} style={{
                  padding: 14, borderRadius: 8,
                  border: '1px solid #374151',
                  background: '#0b1221'
                }}>
                  <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 10 }}>
                    <div>
                      <div style={{ fontWeight: 700, color: '#ef4444', fontSize: 13, marginBottom: 4 }}>
                        {a.label}
                      </div>
                      <div style={{ fontSize: 11, color: '#64748b' }}>
                        <code style={{ color: '#f59e0b' }}>{a.endpoint()}</code>
                      </div>
                      <div style={{ fontSize: 11, color: '#94a3b8', marginTop: 4 }}>{a.desc}</div>
                    </div>
                    <button
                      className="btn btn-danger"
                      style={{ whiteSpace: 'nowrap', minWidth: 80 }}
                      onClick={() => attack(a.endpoint(), a.label)}
                      disabled={loading}
                    >
                      Attack
                    </button>
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Response */}
          {response && (
            <div className="card">
              <div className="card-title">
                📥 Response — {response.url}
                <span className={`badge ${response.status === 200 ? 'badge-danger' : 'badge-success'}`}
                  style={{ marginLeft: 'auto' }}>
                  HTTP {response.status}
                </span>
              </div>
              {response.status === 200 && (
                <div style={{
                  padding: '8px 12px', borderRadius: 6,
                  background: 'rgba(239,68,68,0.1)',
                  border: '1px solid #ef4444',
                  color: '#ef4444', fontSize: 12,
                  fontWeight: 700, marginBottom: 10
                }}>
                  🚨 TẤN CÔNG THÀNH CÔNG — Dữ liệu nhạy cảm bị lộ mà không cần token!
                </div>
              )}
              <div className="code-box" style={{ fontSize: 11, maxHeight: 220, overflow: 'auto' }}>
                {JSON.stringify(response.data, null, 2)}
              </div>
            </div>
          )}
        </div>

        {/* Log & Giải thích */}
        <div>
          <div className="card" style={{ height: 'fit-content', position: 'sticky', top: 24 }}>
            <div className="card-title">
              Attack Log
              <button className="btn btn-ghost" style={{ marginLeft: 'auto', padding: '4px 10px', fontSize: 11 }}
                onClick={() => setLogs([])}>Clear</button>
            </div>
            <div className="attack-log" style={{ height: '260px' }}>
              {logs.length === 0 ? (
                <div style={{ color: '#475569', fontStyle: 'italic', padding: '8px' }}>Chưa có log...</div>
              ) : (
                logs.map(l => (
                  <div key={l.id} className={`log-entry ${l.type}`}>
                    <span style={{ color: '#475569' }}>[{l.t}]</span> {l.msg}
                  </div>
                ))
              )}
            </div>

            <div style={{ marginTop: 14, padding: 12, background: '#060e1a', borderRadius: 7, fontSize: 12 }}>
              <div style={{ fontWeight: 700, color: '#ef4444', marginBottom: 8 }}>Lỗ Hổng v1 — Không Có Bảo Mật</div>
              <div style={{ color: '#94a3b8', lineHeight: 1.7 }}>
                <div style={{ marginBottom: 6 }}>
                  <span style={{ color: '#ef4444' }}>❌ Unauthorized Access:</span> API trả về dữ liệu nhạy cảm mà
                  không yêu cầu bất kỳ token hay session nào.
                </div>
                <div style={{ marginBottom: 6 }}>
                  <span style={{ color: '#ef4444' }}>❌ IDOR:</span> Thay đổi ID trong URL để xem/sửa dữ liệu của
                  người dùng khác — không có kiểm tra ownership.
                </div>
                <div>
                  <span style={{ color: '#22c55e' }}>✅ Fix:</span> Thêm JWT authentication + kiểm tra quyền sở hữu
                  trong mỗi endpoint.
                </div>
              </div>
            </div>

            <div style={{ marginTop: 12, padding: 12, background: '#060e1a', borderRadius: 7, fontSize: 12 }}>
              <div style={{ fontWeight: 700, color: '#f59e0b', marginBottom: 8 }}>Demo Tấn Công</div>
              <div style={{ color: '#94a3b8', lineHeight: 1.7 }}>
                <div>1. Nhấn <strong style={{ color: '#ef4444' }}>Xem Profile User</strong> với userId=1, 2, 3</div>
                <div>2. Nhấn <strong style={{ color: '#ef4444' }}>Xem toàn bộ User</strong> → quyền admin!</div>
                <div>3. Thay orderId → Nhấn <strong style={{ color: '#ef4444' }}>IDOR Order</strong></div>
                <div style={{ marginTop: 6, color: '#475569' }}>
                  Đảm bảo server-no-auth đang chạy tại port 4000.
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
