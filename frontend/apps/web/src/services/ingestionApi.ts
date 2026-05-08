/**
 * 功能：数据接入 API 封装
 * 时间：2026-05-08
 * 作者：AxeXie
 */
import api from './api';

export const ingestionApi = {
  listDataSources: (params?: any) => api.get('/datasources', { params }),
  createDataSource: (data: any) => api.post('/datasources', data),
  testConnection: (id: number) => api.post(`/datasources/${id}/test`),
  listTasks: (params?: any) => api.get('/datasources/tasks', { params }),
  createTask: (data: any) => api.post('/datasources/tasks', data),
  syncTask: (id: number) => api.post(`/datasources/tasks/${id}/sync`),
  getTaskStatus: (id: number) => api.get(`/datasources/tasks/${id}/status`),
};
