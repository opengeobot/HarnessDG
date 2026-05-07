/**
 * 功能：应用路由配置
 * 时间：2026-05-07
 * 作者：AxeXie
 */
import { Routes, Route, Navigate } from 'react-router-dom';
import { lazy, Suspense } from 'react';
import AppLayout from '@/layouts/AppLayout';

const TaskCenter = lazy(() => import('@/pages/task-center/index'));
const BuildMetric = lazy(() => import('@/pages/build-metric/index'));
const AskData = lazy(() => import('@/pages/ask-data/index'));
const Ontology = lazy(() => import('@/pages/ontology/index'));
const Governance = lazy(() => import('@/pages/governance/index'));
const Query = lazy(() => import('@/pages/query/index'));
const Diagnosis = lazy(() => import('@/pages/diagnosis/index'));
const Settings = lazy(() => import('@/pages/settings/index'));
const UserList = lazy(() => import('@/pages/settings/users/UserList'));
const RoleList = lazy(() => import('@/pages/settings/roles/RoleList'));
const ProfilePage = lazy(() => import('@/pages/settings/profile/ProfilePage'));
const DictionaryPage = lazy(() => import('@/pages/settings/dictionary/DictionaryPage'));
const SystemConfigPage = lazy(() => import('@/pages/settings/config/SystemConfigPage'));
const AuditPage = lazy(() => import('@/pages/settings/audit/AuditPage'));

function AppRoutes() {
  return (
    <Suspense fallback={<div>Loading...</div>}>
      <Routes>
        <Route path="/" element={<AppLayout />}>
          <Route index element={<Navigate to="/tasks" replace />} />
          <Route path="tasks" element={<TaskCenter />} />
          <Route path="tasks/build-metric" element={<BuildMetric />} />
          <Route path="tasks/ask-data" element={<AskData />} />
          <Route path="ontology" element={<Ontology />} />
          <Route path="governance" element={<Governance />} />
          <Route path="query" element={<Query />} />
          <Route path="diagnosis" element={<Diagnosis />} />
          <Route path="settings" element={<Settings />}>
            <Route index element={<Navigate to="/settings/profile" replace />} />
            <Route path="users" element={<UserList />} />
            <Route path="roles" element={<RoleList />} />
            <Route path="profile" element={<ProfilePage />} />
            <Route path="dictionary" element={<DictionaryPage />} />
            <Route path="config" element={<SystemConfigPage />} />
            <Route path="audit" element={<AuditPage />} />
          </Route>
        </Route>
      </Routes>
    </Suspense>
  );
}

export default AppRoutes;
