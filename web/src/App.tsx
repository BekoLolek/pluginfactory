import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import ErrorBoundary from '@/components/ErrorBoundary';
import NotificationToast from '@/components/NotificationToast';
import ProtectedRoute from '@/components/ProtectedRoute';
import DashboardLayout from '@/layouts/DashboardLayout';
import LoginPage from '@/pages/LoginPage';
import AuthCallbackPage from '@/pages/AuthCallbackPage';
import DashboardPage from '@/pages/DashboardPage';
import NewBuildPage from '@/pages/NewBuildPage';
import BuildsPage from '@/pages/BuildsPage';
import BuildDetailPage from '@/pages/BuildDetailPage';
import MarketplacePage from '@/pages/MarketplacePage';
import PluginDetailPage from '@/pages/PluginDetailPage';
import PublishPluginPage from '@/pages/PublishPluginPage';
import MyListingsPage from '@/pages/MyListingsPage';
import MyPurchasesPage from '@/pages/MyPurchasesPage';
import SubscriptionPage from '@/pages/SubscriptionPage';
import SettingsPage from '@/pages/SettingsPage';
import TeamDashboardPage from '@/pages/TeamDashboardPage';
import TeamDetailPage from '@/pages/TeamDetailPage';
import SharedWorkspacePage from '@/pages/SharedWorkspacePage';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

export default function App() {
  return (
    <ErrorBoundary>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <Routes>
            <Route path="/" element={<LoginPage />} />
            <Route path="/auth/callback" element={<AuthCallbackPage />} />

            <Route
              path="/dashboard"
              element={
                <ProtectedRoute>
                  <DashboardLayout />
                </ProtectedRoute>
              }
            >
              <Route index element={<DashboardPage />} />
              <Route path="builds/new" element={<NewBuildPage />} />
              <Route path="builds" element={<BuildsPage />} />
              <Route path="builds/:id" element={<BuildDetailPage />} />
              <Route path="marketplace" element={<MarketplacePage />} />
              <Route path="marketplace/publish" element={<PublishPluginPage />} />
              <Route path="marketplace/my-listings" element={<MyListingsPage />} />
              <Route path="marketplace/my-purchases" element={<MyPurchasesPage />} />
              <Route path="marketplace/:id" element={<PluginDetailPage />} />
              <Route path="teams" element={<TeamDashboardPage />} />
              <Route path="teams/:teamId" element={<TeamDetailPage />} />
              <Route path="teams/:teamId/workspaces/:workspaceId" element={<SharedWorkspacePage />} />
              <Route path="settings" element={<SettingsPage />} />
              <Route path="settings/subscription" element={<SubscriptionPage />} />
            </Route>

            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        </BrowserRouter>
        <NotificationToast />
      </QueryClientProvider>
    </ErrorBoundary>
  );
}
