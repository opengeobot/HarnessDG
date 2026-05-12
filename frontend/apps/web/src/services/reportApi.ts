/**
 * 功能：周报 API 封装
 * 时间：2026-05-08
 * 作者：AxeXie
 */
import api from './api';

export const reportApi = {
  listReports: (params?: any) => api.get('/v1/reports', { params }),
  getReport: (id: number) => api.get(`/v1/reports/${id}`),
  createReport: (data: any) => api.post('/v1/reports', data),
};
