import { useEffect, useState } from 'react'
import './App.css'

function App() {
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    fetch('/api/hello')
      .then((res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        return res.json()
      })
      .then(setData)
      .catch((err) => setError(err.message))
  }, [])

  return (
    <main style={{ padding: 40, fontFamily: 'system-ui, sans-serif' }}>
      <h1>React + Vite + Spring Boot</h1>
      {error && <p style={{ color: 'crimson' }}>后端连接失败：{error}</p>}
      {data ? (
        <div>
          <p>✅ 后端返回：<strong>{data.message}</strong></p>
          <p style={{ color: '#666' }}>服务器时间：{data.time}</p>
        </div>
      ) : (
        !error && <p>加载中…</p>
      )}
    </main>
  )
}

export default App
