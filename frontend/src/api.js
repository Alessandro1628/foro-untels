const messages = {
  400: 'Revisa los campos ingresados.',
  401: 'Inicia sesión de nuevo para continuar.',
  403: 'Tu cuenta no tiene permiso para esta acción.',
  404: 'El contenido ya no está disponible.',
  409: 'El contenido ha cambiado. Actualiza la vista.',
  503: 'El servicio no está disponible en este momento. Inténtalo de nuevo.',
}

export async function api(path, { method = 'GET', token, data, signal } = {}) {
  const response = await fetch('/api/v1/' + path, {
    method, signal,
    headers: {
      ...(token && { Authorization: 'Bearer ' + token }),
      ...(data && { 'Content-Type': 'application/json' }),
    },
    ...(data && { body: JSON.stringify(data) }),
  })
  if (!response.ok) {
    const error = new Error(messages[response.status] || 'No se pudo completar la solicitud.')
    error.status = response.status
    throw error
  }
  return response.status === 204 ? null : response.json()
}
