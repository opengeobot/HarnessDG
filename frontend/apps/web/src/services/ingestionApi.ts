/**
 * 功能：数据接入 API 封装
 * 时间：2026-05-08
 * 作者：AxeXie
 */
import api from './api';

export const ingestionApi = {
  listDataSources: (params?: any) => api.get('/v1/datasources', { params }),
  createDataSource: (data: any) => api.post('/v1/datasources', data),
  testConnection: (id: number) => api.post(`/v1/datasources/${id}/test`),
  listTasks: (params?: any) => api.get('/v1/datasources/tasks', { params }),
  createTask: (data: any) => api.post('/v1/datasources/tasks', data),
  syncTask: (id: number) => api.post(`/v1/datasources/tasks/${id}/sync`),
  getTaskStatus: (id: number) => api.get(`/v1/datasources/tasks/${id}/status`),
};
