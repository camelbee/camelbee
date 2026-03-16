import { Routes, Route, Navigate } from 'react-router-dom';
import { NavBar } from '@/components/NavBar';
import { ErrorBoundary } from '@/components/ErrorBoundary';
import { DebuggerPage } from '@/pages/DebuggerPage';
import { MetricsPage } from '@/pages/MetricsPage';
import { SettingsPage } from '@/pages/SettingsPage';
import { useThemeSync } from '@/hooks/useTheme';

export default function App() {
  useThemeSync();

  return (
    <div className="flex h-screen flex-col bg-gray-100 text-gray-800 dark:bg-gray-950 dark:text-gray-200">
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