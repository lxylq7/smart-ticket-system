import http from './http'

// The module centralizes the existing backend contract for later business pages.
export function listTickets(params) {
  return http.get('/tickets', { params })
}

export function listMyTickets(params) {
  return http.get('/tickets/my', { params })
}

export function getTicket(id) {
  return http.get(`/tickets/${id}`)
}

export function createTicket(payload) {
  return http.post('/tickets', payload)
}

export function updateTicket(id, payload) {
  return http.put(`/tickets/${id}`, payload)
}

export function assignTicket(id, payload) {
  return http.put(`/tickets/${id}/assignment`, payload)
}

export function changeTicketStatus(id, payload) {
  return http.put(`/tickets/${id}/status`, payload)
}

export function changeTicketPriority(id, payload) {
  return http.put(`/tickets/${id}/priority`, payload)
}

export function retryTicketClassification(id) {
  return http.post(`/tickets/${id}/classification/retry`)
}

export function listTicketMessages(id) {
  return http.get(`/tickets/${id}/messages`)
}

export function createTicketMessage(id, payload) {
  return http.post(`/tickets/${id}/messages`, payload)
}
