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
const DictionaryAdmin = lazy(() => import('@/pages/settings/dictionary/DictionaryAdmin'));
const SystemConfigPage = lazy(() => import('@/pages/settings/config/SystemConfigPage'));
const AuditPage = lazy(() => import('@/pages/settings/audit/AuditPage'));
const LoginPage = lazy(() => import('@/pages/login/LoginPage'));

// Phase 2: 审批中心
const ApprovalList = lazy(() => import('@/pages/approval/ApprovalList'));
const ApprovalDetail = lazy(() => import('@/pages/approval/ApprovalDetail'));

// Phase 2: 质量规则
const QualityRules = lazy(() => import('@/pages/quality/index'));

// Phase 2: 血缘图
const LineageGraph = lazy(() => import('@/pages/lineage/index'));

// Phase 2: 数据接入
const DataIngestion = lazy(() => import('@/pages/data-ingestion/index'));
const IngestionWizard = lazy(() => import('@/pages/data-ingestion/IngestionWizard'));

// Phase 2: 周报
const WeeklyReportList = lazy(() => import('@/pages/weekly-report/index'));
const ReportView = lazy(() => import('@/pages/weekly-report/ReportView'));

function AppRoutes() {
  return (
    <Suspense fallback={<div>Loading...</div>}>
      <Routes>
        {/* 登录页 - 不需要 AppLayout */}
        <Route path="/login" element={<LoginPage />} />

        {/* 主应用路由 - 需要认证 */}
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
            <Route path="dictionary-admin" element={<DictionaryAdmin />} />
            <Route path="config" element={<SystemConfigPage />} />
            <Route path="audit" element={<AuditPage />} />
          </Route>

          {/* Phase 2: 审批中心 */}
          <Route path="approval" element={<ApprovalList />} />
          <Route path="approval/:id" element={<ApprovalDetail />} />

          {/* Phase 2: 质量规则 */}
          <Route path="quality" element={<QualityRules />} />

          {/* Phase 2: 血缘图 */}
          <Route path="lineage" element={<LineageGraph />} />

          {/* Phase 2: 数据接入 */}
          <Route path="data-ingestion" element={<DataIngestion />} />
          <Route path="data-ingestion/wizard" element={<IngestionWizard />} />
          <Route path="data-ingestion/wizard/:id" element={<IngestionWizard />} />

          {/* Phase 2: 周报 */}
          <Route path="weekly-report" element={<WeeklyReportList />} />
          <Route path="weekly-report/:id" element={<ReportView />} />
        </Route>
      </Routes>
    </Suspense>
  );
}

export default AppRoutes;
