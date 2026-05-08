/**
 * 功能：审批模块 API 封装
 * 时间：2026-05-08
 * 作者：AxeXie
 */
import api from './api';

export const approvalApi = {
  listTemplates: (params?: any) => api.get('/approval/templates', { params }),
  createTemplate: (data: any) => api.post('/approval/templates', data),
  listInstances: (params?: any) => api.get('/approval/instances', { params }),
  getInstance: (id: number) => api.get(`/approval/instances/${id}`),
  createInstance: (data: any) => api.post('/approval/instances', data),
  approveStep: (instanceId: number, stepId: number, data?: any) =>
    api.post(`/approval/instances/${instanceId}/steps/${stepId}/approve`, data),
  rejectStep: (instanceId: number, stepId: number, data: any) =>
    api.post(`/approval/instances/${instanceId}/steps/${stepId}/reject`, data),
  myTodo: (params?: any) => api.get('/approval/my-todo', { params }),
};
