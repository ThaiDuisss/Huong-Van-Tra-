// Isolated verification harness. Never imported by the POS application.
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { AuthContext } from '../../web-app/apps/pos-web/src/auth/auth-context';
import { AppRouter } from '../../web-app/apps/pos-web/src/app/AppRouter';
import { AiReportTable } from '../../web-app/apps/pos-web/src/features/ai/components/AiReportTable';
import { AiReportEditDialog } from '../../web-app/apps/pos-web/src/features/ai/components/AiReportEditDialog';
import { validateAiReportFile, validateAiReportMetadata } from '../../web-app/apps/pos-web/src/features/ai/utils/ai-report.validation';
import { filterSystemNavigationByRoles, systemNavigationItems, findNavigationTrail, isNavigationItemActive } from '../../web-app/apps/pos-web/src/components/layout/System/system-navigation';
import { filterPosNavigationByRoles, posNavigationItems } from '../../web-app/apps/pos-web/src/components/layout/Pos/pos-navigation';
import { BACKOFFICE_ROLES } from '../../web-app/apps/pos-web/src/auth/role-utils';
import { getAiReportApi } from '../../web-app/apps/pos-web/src/features/ai/services/aiReport.api';
import '../../web-app/apps/pos-web/src/style/index.css';

const params = new URLSearchParams(location.search);
const role = params.get('role') || 'SALE_STAFF';
const mode = params.get('mode');
const fixture = { id: 'isolated-ui-fixture', name: 'Báo cáo kiểm tra giao diện', version: '1.0', fileId: 'fixture-file', fileName: 'kiem-tra.pdf', fileType: 'PDF' as const, createdAt: '2026-10-09T09:00:00Z', updatedAt: '2026-10-09T09:00:00Z' };
const auth = { initialized: true, authenticated: true, roles: [role], displayName: 'Kiểm tra giao diện', roleLabel: role === 'SALE_STAFF' ? 'Nhân viên bán hàng' : 'Quản trị viên', login() {}, register() {}, async logout() {}, hasRealmRole: (value: string) => value === role, hasResourceRole: () => false };
Object.assign(window, { aiVerification: { validateAiReportFile, validateAiReportMetadata, filterSystemNavigationByRoles, filterPosNavigationByRoles, posNavigationItems, systemNavigationItems, findNavigationTrail, isNavigationItemActive, BACKOFFICE_ROLES, getAiReportApi } });
const state = mode === 'loading' ? { status: 'loading' as const } : mode === 'error' ? { status: 'error' as const, message: 'Không thể tải danh sách báo cáo.' } : { status: 'ready' as const, reports: mode === 'row' ? [fixture] : [] };
createRoot(document.getElementById('root')!).render(
  <AuthContext.Provider value={auth}>
    <BrowserRouter>
      {mode === 'edit' ? <AiReportEditDialog report={fixture} onClose={() => {}} />
        : mode ? <main className="p-6"><AiReportTable state={state} selectedId={null} busy={false} fileActionsAvailable={false} onSelect={() => {}} onView={() => {}} onEdit={() => {}} onExport={() => {}} onRetry={() => { document.body.dataset.retried = 'true'; }} /></main>
          : <AppRouter />}
    </BrowserRouter>
  </AuthContext.Provider>,
);
