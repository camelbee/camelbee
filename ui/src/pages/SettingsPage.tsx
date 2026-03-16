import { useSettingsStore } from '@/store/settingsStore';

export function SettingsPage() {
  const {
    theme, setTheme,
    healthUrl, setHealthUrl,
    healthRefreshRate, setHealthRefreshRate,
    metricsUrl, setMetricsUrl,
    metricsHistory, setMetricsHistory,
    metricsRefreshRate, setMetricsRefreshRate,
    maxTextFieldChars, setMaxTextFieldChars,
  } = useSettingsStore();

  return (
    <div className="flex flex-1 items-center justify-center">
      <div className="w-full max-w-xl space-y-8 px-4">
        {/* Theme */}
        <Row label="theme">
          <div className="flex gap-2">
            <button
              onClick={() => setTheme('light')}
              className={`rounded px-5 py-1.5 text-sm font-medium transition ${
                theme === 'light'
                  ? 'bg-amber-500 text-white'
                  : 'bg-gray-200 text-gray-500 hover:bg-gray-300 dark:bg-gray-700 dark:text-gray-400 dark:hover:bg-gray-600'
              }`}
            >
              Light
            </button>
            <button
              onClick={() => setTheme('dark')}
              className={`rounded px-5 py-1.5 text-sm font-medium transition ${
                theme === 'dark'
                  ? 'bg-blue-600 text-white'
                  : 'bg-gray-200 text-gray-500 hover:bg-gray-300 dark:bg-gray-700 dark:text-gray-400 dark:hover:bg-gray-600'
              }`}
            >
              Dark
            </button>
          </div>
        </Row>

        <div className="border-t border-gray-300 dark:border-gray-700" />

        {/* Health URL */}
        <Row label="health url">
          <input
            type="text"
            value={healthUrl}
            onChange={(e) => setHealthUrl(e.target.value)}
            className="w-72 rounded border border-gray-300 bg-gray-50 px-3 py-1.5 text-sm text-gray-800 focus:border-blue-500 focus:outline-none dark:border-gray-600 dark:bg-gray-700 dark:text-gray-200"
          />
        </Row>

        {/* Health refresh rate */}
        <Row label="health refresh rate">
          <input
            type="number"
            min={2}
            max={10}
            value={healthRefreshRate}
            onChange={(e) => setHealthRefreshRate(Number(e.target.value))}
            className="w-20 rounded border border-gray-300 bg-gray-50 px-3 py-1.5 text-sm text-gray-800 focus:border-blue-500 focus:outline-none dark:border-gray-600 dark:bg-gray-700 dark:text-gray-200"
          />
          <span className="text-sm text-gray-500 dark:text-gray-400">secs [2 to 10]</span>
        </Row>

        {/* Metrics URL */}
        <Row label="metrics url">
          <input
            type="text"
            value={metricsUrl}
            onChange={(e) => setMetricsUrl(e.target.value)}
            className="w-72 rounded border border-gray-300 bg-gray-50 px-3 py-1.5 text-sm text-gray-800 focus:border-blue-500 focus:outline-none dark:border-gray-600 dark:bg-gray-700 dark:text-gray-200"
          />
        </Row>

        {/* Metrics history */}
        <Row label="metrics history">
          <input
            type="number"
            min={300}
            max={600}
            value={metricsHistory}
            onChange={(e) => setMetricsHistory(Number(e.target.value))}
            className="w-20 rounded border border-gray-300 bg-gray-50 px-3 py-1.5 text-sm text-gray-800 focus:border-blue-500 focus:outline-none dark:border-gray-600 dark:bg-gray-700 dark:text-gray-200"
          />
          <span className="text-sm text-gray-500 dark:text-gray-400">secs [300 to 600]</span>
        </Row>

        {/* Metrics refresh rate */}
        <Row label="metrics refresh rate">
          <input
            type="number"
            min={2}
            max={10}
            value={metricsRefreshRate}
            onChange={(e) => setMetricsRefreshRate(Number(e.target.value))}
            className="w-20 rounded border border-gray-300 bg-gray-50 px-3 py-1.5 text-sm text-gray-800 focus:border-blue-500 focus:outline-none dark:border-gray-600 dark:bg-gray-700 dark:text-gray-200"
          />
          <span className="text-sm text-gray-500 dark:text-gray-400">secs [2 to 10]</span>
        </Row>

        {/* Max chars */}
        <Row label="max characters in a text field">
          <input
            type="number"
            min={1000}
            max={30000}
            value={maxTextFieldChars}
            onChange={(e) => setMaxTextFieldChars(Number(e.target.value))}
            className="w-24 rounded border border-gray-300 bg-gray-50 px-3 py-1.5 text-sm text-gray-800 focus:border-blue-500 focus:outline-none dark:border-gray-600 dark:bg-gray-700 dark:text-gray-200"
          />
          <span className="text-sm text-gray-500 dark:text-gray-400">characters [1000 to 30000]</span>
        </Row>

      </div>
    </div>
  );
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex items-center justify-end gap-4">
      <span className="text-sm text-gray-600 text-right min-w-[200px] dark:text-gray-300">{label}</span>
      <div className="flex items-center gap-2">{children}</div>
    </div>
  );
}
