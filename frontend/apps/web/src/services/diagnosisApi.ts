/**
 * 功能：诊断 API 封装
 * 时间：2026-05-08
 * 作者：AxeXie
 */
import api from './api';

export const diagnosisApi = {
  runDiagnosis: (data: any) => api.post('/diagnosis/run', data),
  getDiagnosis: (id: number) => api.get(`/diagnosis/${id}`),
  remediate: (id: number, data?: any) => api.post(`/diagnosis/${id}/remediate`, data),
};
