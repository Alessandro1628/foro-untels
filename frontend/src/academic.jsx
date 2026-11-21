export function Notice({ action, sources = [] }) {
  const error = sources.find((source) => source.error)?.error
  return (
    <>
      {error && <p role="alert">{error}</p>}
      {action?.message && <p role="status">{action.message}</p>}
    </>
  )
}

export function SubjectSelect({ rows, name = 'subject', value, onChange, all = false }) {
  return (
    <label>
      Asignatura
      <select name={name} value={value} onChange={onChange} required={!all}>
        <option value="">{all ? 'Todas las asignaturas' : 'Selecciona una asignatura'}</option>
        {rows.map((s) => (
          <option key={s.id} value={s.id}>
            {s.name}
          </option>
        ))}
      </select>
    </label>
  )
}

export function Form({ children, onSave, submit, disabled = false }) {
  return (
    <form
      onSubmit={async (event) => {
        event.preventDefault()
        const form = event.currentTarget
        if (await onSave(new FormData(form))) form.reset()
      }}
    >
      {children}
      <button disabled={disabled}>{submit}</button>
    </form>
  )
}
