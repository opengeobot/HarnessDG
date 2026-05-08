/**
 * 功能：审批模块 API 封装
 * 时间：2026-05-08
 * 作者：AxeXie
 */
import api from './api';

export const approvalApi = {
  listTemplates: (params?: any) => api.get('/v1/approval/templates', { params }),
  createTemplate: (data: any) => api.post('/v1/approval/templates', data),
  listInstances: (params?: any) => api.get('/v1/approval/instances', { params }),
  getInstance: (id: number) => api.get(`/v1/approval/instances/${id}`),
  createInstance: (data: any) => api.post('/v1/approval/instances', data),
  approveStep: (instanceId: number, stepId: number, data?: any) =>
    api.post(`/v1/approval/instances/${instanceId}/steps/${stepId}/approve`, data),
  rejectStep: (instanceId: number, stepId: number, data: any) =>
    api.post(`/v1/approval/instances/${instanceId}/steps/${stepId}/reject`, data),
  myTodo: (params?: any) => api.get('/v1/approval/my-todo', { params }),
};
