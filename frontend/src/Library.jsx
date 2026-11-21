import { useEffect, useState } from 'react'
import { statusText, useRows, useActions } from './useAcademic'
import { Form, Notice, SubjectSelect } from './academic'
import BookPicker from './BookPicker'

function Material({ item, token }) {
  const [preview, setPreview] = useState('')
  const [error, setError] = useState('')
  const [open, setOpen] = useState(false)
  useEffect(() => {
    if (!open || item.kind !== 'PDF') return
    const controller = new AbortController()
    let objectUrl
    fetch('/api/v1/academic/materials/' + item.id + '/pdf', {
      headers: token ? { Authorization: 'Bearer ' + token } : {},
      signal: controller.signal,
    })
      .then(async (response) => {
        if (!response.ok) throw Error('No se pudo abrir el documento.')
        const blob = await response.blob()
        if (!controller.signal.aborted) {
          objectUrl = URL.createObjectURL(blob)
          setPreview(objectUrl)
        }
      })
      .catch((err) => {
        if (!controller.signal.aborted) setError(err.message)
      })
    return () => {
      controller.abort()
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [open, item.id, item.kind, item.version, token])
  return (
    <article>
      <h4>{item.title}</h4>
      <p>
        {item.subject} · {item.kind === 'PDF' ? 'Documento PDF' : 'Enlace académico'}
      </p>
      <p>
        Autoría del material: {item.author}
        <br />
        Compartido por {item.shared_by}
        {item.teacher && ' · Docente UNTELS'}
      </p>
      {item.kind === 'LINK' ? (
        <a href={item.url} target="_blank" rel="noreferrer">
          Abrir recurso externo
        </a>
      ) : (
        <button
          onClick={() => {
            setOpen(!open)
            setPreview('')
            setError('')
          }}
        >
          {open ? 'Cerrar documento' : 'Leer documento'}
        </button>
      )}
      {open && preview && <iframe title={item.title} src={preview} width="100%" height="440" />}
      {error && <p role="alert">{error}</p>}
    </article>
  )
}

function ReviewHistory({ item, token, revision }) {
  const [open, setOpen] = useState(false)
  const history = useRows(open && 'academic/materials/' + item.id + '/history', token, revision)
  return (
    <>
      <button onClick={() => setOpen(!open)}>
        {open ? 'Cerrar historial' : 'Historial de revisión'}
      </button>
      {open && (
        <>
          <Notice sources={[history]} />
          {history.rows.map((r) => (
            <p key={r.version}>
              Versión {r.version} · {statusText(r.decision)} {r.reason}
            </p>
          ))}
          {!history.loading && !history.rows.length && <p>Sin decisiones registradas.</p>}
        </>
      )}
    </>
  )
}
export default function Library({ auth }) {
  const { token, user } = auth
  const action = useActions(token)
  const [q, setQ] = useState('')
  const [subject, setSubject] = useState('')
  const [revision, setRevision] = useState(null)
  const [format, setFormat] = useState('PDF')
  const [book, setBook] = useState(null)
  const subjects = useRows('academic/catalogs/subjects', token, action.revision)
  const catalog = useRows(
    'academic/materials?' + new URLSearchParams({ q, ...(subject && { subject }) }),
    token,
    action.revision,
  )
  const mine = useRows(token && 'academic/materials/mine', token, action.revision)
  const pending = useRows(user?.role === 'ADMIN' && 'academic/materials/pending', token, action.revision)
  return (
    <section className="academico">
      <h1>Biblioteca académica</h1>
      <Notice action={action} sources={[catalog, subjects, mine, pending]} />
      <label>
        Buscar por título
        <input type="search" value={q} onChange={(e) => setQ(e.target.value)} />
      </label>
      <SubjectSelect
        all
        rows={subjects.rows}
        value={subject}
        onChange={(e) => setSubject(e.target.value)}
      />
      {catalog.rows.map((item) => (
        <Material key={item.id} item={item} token={token} />
      ))}
      {!catalog.loading && !catalog.error && !catalog.rows.length && (
        <p>No hay materiales publicados para esta búsqueda.</p>
      )}
      <h2>Libros de Google Books</h2>
      <BookPicker book={book} onSelect={setBook} browse />
      {token && (
        <>
          <h2>{revision ? 'Corregir aporte #' + revision.id : 'Enviar material a revisión'}</h2>
          <Form
            key={revision?.id || 'new'}
            submit="Enviar a revisión"
            disabled={action.busy}
            onSave={async (data) => {
              if (revision) data.set('revision', revision.id)
              const ok = await action.act(
                'academic/materials',
                'POST',
                data,
                'Aporte enviado a revisión.',
              )
              if (ok) setRevision(null)
              return ok
            }}
          >
            <label>
              Título
              <input name="title" defaultValue={revision?.title} required maxLength={180} />
            </label>
            <label>
              Autor del material
              <input name="author" defaultValue={revision?.author} required maxLength={160} />
            </label>
            <SubjectSelect rows={subjects.rows.filter((s) => s.active)} />
            <label>
              Tipo
              <select value={format} onChange={(e) => setFormat(e.target.value)}>
                <option>PDF</option>
                <option value="LINK">Enlace</option>
              </select>
            </label>
            {format === 'PDF' ? (
              <label>
                Documento PDF (máximo 4 MB)
                <input type="file" name="file" accept="application/pdf" required />
              </label>
            ) : (
              <label>
                Enlace académico
                <input name="url" type="url" defaultValue={revision?.url} required />
              </label>
            )}
          </Form>
          {revision && <button onClick={() => setRevision(null)}>Cancelar corrección</button>}
          <h2>Mis aportes</h2>
          {mine.rows.map((m) => (
            <article key={m.id}>
              <strong>{m.title}</strong> · {statusText(m.status)} · Versión {m.version}
              <ReviewHistory item={m} token={token} revision={action.revision} />
              {m.observation && <p>Observación: {m.observation}</p>}
              {m.status === 'OBSERVED' && (
                <button
                  onClick={() => {
                    setRevision(m)
                    setFormat(m.kind)
                  }}
                >
                  Corregir aporte
                </button>
              )}
            </article>
          ))}
        </>
      )}
      {user?.role === 'ADMIN' && (
        <>
          <h2>Revisión de materiales</h2>
          {pending.rows.map((m) => (
            <div key={m.id}>
              <Material item={m} token={token} />
              <button
                disabled={action.busy}
                onClick={() =>
                  action.act('academic/materials/' + m.id + '/review', 'PUT', {
                    approve: true,
                    version: m.version,
                  })
                }
              >
                Aprobar material
              </button>
              <Form
                submit="Observar"
                disabled={action.busy}
                onSave={(data) =>
                  action.act('academic/materials/' + m.id + '/review', 'PUT', {
                    approve: false,
                    version: m.version,
                    reason: data.get('reason'),
                  })
                }
              >
                <label>
                  Motivo
                  <input name="reason" maxLength={500} required />
                </label>
              </Form>
            </div>
          ))}
        </>
      )}
    </section>
  )
}
