import { useEffect, useState } from 'react'
import { api } from './api'

export function useRows(path, token, revision = 0) {
  const [result, setResult] = useState(null)
  useEffect(() => {
    if (!path) return
    const controller = new AbortController()
    const finish = (rows, error) => {
      if (!controller.signal.aborted) setResult({ path, token, revision, rows, error })
    }
    api(path, { token, signal: controller.signal })
      .then((rows) => finish(rows, ''))
      .catch((err) => finish([], err.message))
    return () => controller.abort()
  }, [path, token, revision])
  return result?.path === path && result?.token === token && result?.revision === revision
    ? result
    : { rows: [], error: '', loading: Boolean(path) }
}

export function useActions(token) {
  const [revision, setRevision] = useState(0)
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)
  async function act(path, method, data, success = 'Cambios guardados.') {
    setBusy(true)
    setMessage('')
    try {
      await api(path, { token, method, data })
      setRevision((n) => n + 1)
      setMessage(success)
      return true
    } catch (err) {
      setMessage(err.message)
      return false
    } finally {
      setBusy(false)
    }
  }
  return { revision, message, busy, act }
}

const statusNames = {
  PENDING: 'Pendiente',
  APPROVED: 'Aprobado',
  OBSERVED: 'Observado',
  REJECTED: 'Rechazado',
  RETRY: 'Pendiente de reintento',
  SYNCED: 'Sincronizado',
}
export function statusText(value) {
  return statusNames[value] || value
}
