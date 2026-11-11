/**
 * 房源模块 API
 */
import request from './index'

export const houseApi = {
  /** 分页列表（支持筛选+排序） */
  list(params = {}) {
    return request.get('/house/list', { params })
  },

  /** 房源详情 */
  detail(id) {
    return request.get(`/house/detail/${id}`)
  },

  /** 发布房源 */
  publish(data) {
    return request.post('/house/publish', data)
  },

  /** 更新房源 */
  update(data) {
    return request.put('/house/update', data)
  },

  /** 删除房源 */
  delete(id) {
    return request.delete(`/house/delete/${id}`)
  },

  /** 修改房源状态（管理员） */
  changeStatus(id, status) {
    return request.put('/house/status', { id, status })
  }
}
