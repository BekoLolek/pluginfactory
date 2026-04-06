import { Outlet } from 'react-router-dom';
import Sidebar from '@/components/Sidebar';

export default function DashboardLayout() {
  return (
    <div className="flex h-screen bg-slate-950 text-white">
      <a
        href="#main-content"
        className="sr-only focus:not-sr-only focus:absolute focus:top-2 focus:left-2 focus:z-50 focus:px-4 focus:py-2 focus:bg-blue-600 focus:text-white focus:rounded-lg focus:text-sm focus:font-medium"
      >
        Skip to content
      </a>
      <Sidebar />
      <main id="main-content" className="flex-1 overflow-y-auto" tabIndex={-1}>
        <div className="p-6 lg:p-8 max-w-7xl mx-auto">
          <Outlet />
        </div>
      </main>
    </div>
  );
}
