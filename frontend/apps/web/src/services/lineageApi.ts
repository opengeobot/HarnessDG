/**
 * 功能：血缘 API 封装
 * 时间：2026-05-08
 * 作者：AxeXie
 */
import api from './api';

export const lineageApi = {
  listNodes: (params?: any) => api.get('/lineage/nodes', { params }),
  getNode: (id: number) => api.get(`/lineage/nodes/${id}`),
  listEdges: (params?: any) => api.get('/lineage/edges', { params }),
  createLineage: (data: any) => api.post('/lineage', data),
  deleteLineage: (id: number) => api.delete(`/lineage/${id}`),
  getMetricLineage: (metricId: number) => api.get(`/lineage/metrics/${metricId}`),
  getImpact: (entityId: number) => api.get(`/lineage/impact/${entityId}`),
};
