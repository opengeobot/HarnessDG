/**
 * 功能：质量规则 API 封装
 * 时间：2026-05-08
 * 作者：AxeXie
 */
import api from './api';

export const qualityApi = {
  listRules: (params?: any) => api.get('/quality/rules', { params }),
  getRule: (id: number) => api.get(`/quality/rules/${id}`),
  createRule: (data: any) => api.post('/quality/rules', data),
  updateRule: (id: number, data: any) => api.put(`/quality/rules/${id}`, data),
  deleteRule: (id: number) => api.delete(`/quality/rules/${id}`),
  autoGenerateRules: (data: any) => api.post('/quality/rules/auto-generate', data),
  listChecks: (params?: any) => api.get('/quality/checks', { params }),
};
