import { useState } from 'react'
import { statusText, useActions, useRows } from './useAcademic'
import { Form, Notice, SubjectSelect } from './academic'

function when(value) {
  return new Date(value).toLocaleString('es-PE')
}
export default function Tutoring({ auth }) {
  const { token, user } = auth
  const action = useActions(token)
  const [subject, setSubject] = useState('')
  const [tutor, setTutor] = useState(null)
  const [kind, setKind] = useState('TUTOR')
  const subjects = useRows('academic/catalogs/subjects', token, action.revision)
  const tutors = useRows(
    'academic/tutors' + (subject ? '?subject=' + subject : ''),
    token,
    action.revision,
  )
  const slots = useRows(tutor && 'academic/slots?credential=' + tutor, token, action.revision)
  const sessions = useRows(token && 'academic/sessions', token, action.revision)
  const mine = useRows(token && 'academic/credentials/mine', token, action.revision)
  const pending = useRows(
    user?.role === 'ADMIN' && 'academic/credentials/pending',
    token,
    action.revision,
  )
  const approved = mine.rows.filter((c) => c.kind === 'TUTOR' && c.status === 'APPROVED')
  return (
    <section className="academico">
      <h1>Tutorías y acreditaciones</h1>
      <Notice action={action} sources={[subjects, tutors, slots, sessions, mine, pending]} />
      <SubjectSelect
        all
        rows={subjects.rows.filter((s) => s.active)}
        value={subject}
        onChange={(e) => {
          setSubject(e.target.value)
          setTutor(null)
        }}
      />
      {tutors.rows.map((t) => (
        <article key={t.id}>
          <strong>{t.full_name}</strong> · {t.subject}
          <button onClick={() => setTutor(t.id)}>Consultar horarios</button>
          {tutor === t.id && (
            <>
              {!slots.loading && !slots.rows.length && <p>Sin horarios disponibles.</p>}
              {slots.rows.map((s) => (
                <p key={s.id}>
                  {when(s.starts_at)} – {when(s.ends_at)}
                  {token && (
                    <button
                      disabled={action.busy}
                      onClick={() =>
                        action.act(
                          'academic/slots/' + s.id + '/reserve',
                          'PUT',
                          null,
                          'Reserva confirmada.',
                        )
                      }
                    >
                      Reservar
                    </button>
                  )}
                </p>
              ))}
            </>
          )}
        </article>
      ))}
      {!tutors.loading && !tutors.rows.length && <p>No hay tutores acreditados para la selección.</p>}
      {token && (
        <>
          <h2>Mis tutorías</h2>
          {sessions.rows.map((s) => (
            <article key={s.id}>
              <strong>{s.subject}</strong> · {when(s.starts_at)}
              <p>
                Tutor: {s.tutor} · Reserva confirmada · Calendario: {statusText(s.sync_state)}
              </p>
              {s.meet_url && (
                <a href={s.meet_url} target="_blank" rel="noreferrer">
                  Ingresar a Google Meet
                </a>
              )}
              {s.rating && <p>Calificación: {s.rating}/5</p>}
              {!s.rating && s.student_id === user.id && new Date(s.ends_at) <= new Date() && (
                <Form
                  submit="Calificar sesión"
                  disabled={action.busy}
                  onSave={(data) =>
                    action.act('academic/sessions/' + s.id + '/rating', 'PUT', {
                      stars: Number(data.get('stars')),
                    })
                  }
                >
                  <label>
                    Puntuación
                    <select name="stars">
                      {[1, 2, 3, 4, 5].map((n) => (
                        <option key={n}>{n}</option>
                      ))}
                    </select>
                  </label>
                </Form>
              )}
            </article>
          ))}
          {approved.length > 0 && (
            <>
              <h2>Publicar disponibilidad</h2>
              <Form
                submit="Publicar horario"
                disabled={action.busy}
                onSave={(data) =>
                  action.act('academic/slots', 'POST', {
                    credential: Number(data.get('credential')),
                    start: new Date(data.get('start')).toISOString(),
                    minutes: Number(data.get('minutes')),
                  })
                }
              >
                <label>
                  Asignatura acreditada
                  <select name="credential">
                    {approved.map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.subject}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Inicio (hora local)
                  <input type="datetime-local" name="start" required />
                </label>
                <label>
                  Duración en minutos
                  <input type="number" name="minutes" min={10} max={240} defaultValue={60} required />
                </label>
              </Form>
            </>
          )}
          <h2>Solicitar acreditación</h2>
          <Form
            submit="Enviar solicitud"
            disabled={action.busy}
            onSave={(data) =>
              action.act('academic/credentials', 'POST', {
                kind,
                subject: kind === 'TUTOR' ? Number(data.get('subject')) : null,
                evidence: data.get('evidence'),
              })
            }
          >
            <label>
              Acreditación
              <select value={kind} onChange={(e) => setKind(e.target.value)}>
                <option value="TUTOR">Tutor</option>
                <option value="TEACHER">Docente UNTELS</option>
              </select>
            </label>
            {kind === 'TUTOR' && <SubjectSelect rows={subjects.rows.filter((s) => s.active)} />}
            <label>
              Sustento privado
              <textarea name="evidence" required maxLength={1500} />
            </label>
          </Form>
          {mine.rows.map((c) => (
            <p key={c.id}>
              {c.kind === 'TUTOR' ? 'Tutor · ' + c.subject : 'Docente UNTELS'} · {statusText(c.status)}
            </p>
          ))}
        </>
      )}
      {user?.role === 'ADMIN' && (
        <>
          <h2>Solicitudes de acreditación</h2>
          {pending.rows.map((c) => (
            <article key={c.id}>
              <strong>{c.full_name}</strong> ·{' '}
              {c.kind === 'TUTOR' ? 'Tutor · ' + c.subject : 'Docente UNTELS'}
              <p>{c.evidence}</p>
              {[true, false].map((approve) => (
                <button
                  key={String(approve)}
                  disabled={action.busy}
                  onClick={() => action.act('academic/credentials/' + c.id, 'PUT', { approve })}
                >
                  {approve ? 'Aprobar' : 'Rechazar'}
                </button>
              ))}
            </article>
          ))}
        </>
      )}
    </section>
  )
}
