import { Routes, Route, Navigate } from 'react-router-dom';
import { NavBar } from '@/components/NavBar';
import { ErrorBoundary } from '@/components/ErrorBoundary';
import { DebuggerPage } from '@/pages/DebuggerPage';
import { MetricsPage } from '@/pages/MetricsPage';
import { SettingsPage } from '@/pages/SettingsPage';

export default function App() {
  return (
    <div className="flex h-screen flex-col bg-gray-950 text-gray-200">
      <NavBar />
      <ErrorBoundary>
        <Routes>
          <Route path="/debugger" element={<DebuggerPage />} />
          <Route path="/metrics" element={<MetricsPage />} />
          <Route path="/settings" element={<SettingsPage />} />
          <Route path="*" element={<Navigate to="/debugger" replace />} />
        </Routes>
      </ErrorBoundary>
    </div>
  );
}
