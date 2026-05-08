/**
 * 功能：质量规则 API 封装
 * 时间：2026-05-08
 * 作者：AxeXie
 */
import api from './api';

export const qualityApi = {
  listRules: (params?: any) => api.get('/v1/quality/rules', { params }),
  getRule: (id: number) => api.get(`/v1/quality/rules/${id}`),
  createRule: (data: any) => api.post('/v1/quality/rules', data),
  updateRule: (id: number, data: any) => api.put(`/v1/quality/rules/${id}`, data),
  deleteRule: (id: number) => api.delete(`/v1/quality/rules/${id}`),
  autoGenerateRules: (data: any) => api.post('/v1/quality/rules/auto-generate', data),
  listChecks: (params?: any) => api.get('/v1/quality/checks', { params }),
};
