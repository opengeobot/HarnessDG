/**
 * 功能：周报 API 封装
 * 时间：2026-05-08
 * 作者：AxeXie
 */
import api from './api';

export const reportApi = {
  listReports: (params?: any) => api.get('/reports/weekly', { params }),
  getReport: (id: number) => api.get(`/reports/weekly/${id}`),
  createReport: (data: any) => api.post('/reports/weekly', data),
};
