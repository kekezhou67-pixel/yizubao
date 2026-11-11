/**
 * 地铁 + 攻略 + 其他 API
 */
import request from './index'

export const subwayApi = {
  cities() { return request.get('/subway/cities') },
  lines(city) { return request.get('/subway/lines', { params: { city } }) },
  stations(city, line) { return request.get('/subway/stations', { params: { city, line } }) }
}

export const guideApi = {
  list(params) { return request.get('/guide/list', { params }) },
  detail(id) { return request.get(`/guide/detail/${id}`) },
  like(id) { return request.post(`/guide/like/${id}`) },
  publish(data) { return request.post('/guide/publish', data) }
}

export const adminApi = {
  stats() { return request.get('/admin/stats') },
  users() { return request.get('/admin/users') },
  toggleUser(id, status) { return request.put(`/admin/user/${id}/status`, { status }) },
  reviews() { return request.get('/admin/reviews') },
  reviewStatus(id, status) { return request.put(`/admin/review/${id}/status`, { status }) },
  guides() { return request.get('/admin/guides') },
  guideStatus(id, status) { return request.put(`/admin/guide/${id}/status`, { status }) },
  contracts() { return request.get('/admin/contracts') }
}

export const favoriteApi = {
  add(houseId) { return request.post('/favorite/add', { houseId }) },
  remove(houseId) { return request.post('/favorite/remove', { houseId }) },
  check(houseId) { return request.get(`/favorite/check/${houseId}`) },
  myList() { return request.get('/favorite/list') }
}

export const reviewApi = {
  submit(data) { return request.post('/landlord-review/submit', data) },
  list(landlordId) { return request.get(`/landlord-review/landlord/${landlordId}`) }
}

export const aiApi = {
  health() { return request.get('/ai/health') },
  qa(question) { return request.post('/ai/qa', { question }) },
  tokenStats(userId) { return request.get('/ai/token/stats', { params: { userId } }) },
  clearMemory(conversationId) { return request.delete('/ai/chat/memory/' + conversationId) },
  kbList(params) { return request.get('/kb/documents', { params }) },
  kbCreate(data) { return request.post('/kb/documents', data) },
  kbUpload(formData) { return request.post('/kb/documents/upload', formData, { timeout: 300000 }) },
  kbDelete(id) { return request.delete('/kb/documents/' + id) },
  kbSearch(query, topK) { return request.post('/kb/search', { query, topK }) },
  searchByImage(formData, topK) { return request.post('/house/search-by-image?topK=' + topK, formData, { timeout: 120000 }) },
  searchByText(description, topK) { return request.post('/house/search-by-text', { description, topK }, { timeout: 120000 }) },
  reviewImage(formData) { return request.post('/contract/review-image', formData, { timeout: 300000 }) },
  clipReindex(limit) { return request.post('/house/clip/reindex?limit=' + (limit || 300), null, { timeout: 1800000 }) }
}
