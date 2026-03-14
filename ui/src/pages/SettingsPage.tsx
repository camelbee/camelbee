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
    pauseOnFocusLost, setPauseOnFocusLost,
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
                  ? 'bg-gray-500 text-white'
                  : 'bg-gray-700 text-gray-400 hover:bg-gray-600'
              }`}
            >
              Light
            </button>
            <button
              onClick={() => setTheme('dark')}
              className={`rounded px-5 py-1.5 text-sm font-medium transition ${
                theme === 'dark'
                  ? 'bg-blue-600 text-white'
                  : 'bg-gray-700 text-gray-400 hover:bg-gray-600'
              }`}
            >
              Dark
            </button>
          </div>
        </Row>

        <div className="border-t border-gray-700" />

        {/* Health URL */}
        <Row label="health url">
          <input
            type="text"
            value={healthUrl}
            onChange={(e) => setHealthUrl(e.target.value)}
            className="w-72 rounded border border-gray-600 bg-gray-700 px-3 py-1.5 text-sm text-gray-200 focus:border-blue-500 focus:outline-none"
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
            className="w-20 rounded border border-gray-600 bg-gray-700 px-3 py-1.5 text-sm text-gray-200 focus:border-blue-500 focus:outline-none"
          />
          <span className="text-sm text-gray-400">secs [2 to 10]</span>
        </Row>

        {/* Metrics URL */}
        <Row label="metrics url">
          <input
            type="text"
            value={metricsUrl}
            onChange={(e) => setMetricsUrl(e.target.value)}
            className="w-72 rounded border border-gray-600 bg-gray-700 px-3 py-1.5 text-sm text-gray-200 focus:border-blue-500 focus:outline-none"
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
            className="w-20 rounded border border-gray-600 bg-gray-700 px-3 py-1.5 text-sm text-gray-200 focus:border-blue-500 focus:outline-none"
          />
          <span className="text-sm text-gray-400">secs [300 to 600]</span>
        </Row>

        {/* Metrics refresh rate */}
        <Row label="metrics refresh rate">
          <input
            type="number"
            min={2}
            max={10}
            value={metricsRefreshRate}
            onChange={(e) => setMetricsRefreshRate(Number(e.target.value))}
            className="w-20 rounded border border-gray-600 bg-gray-700 px-3 py-1.5 text-sm text-gray-200 focus:border-blue-500 focus:outline-none"
          />
          <span className="text-sm text-gray-400">secs [2 to 10]</span>
        </Row>

        {/* Max chars */}
        <Row label="max characters in a text field">
          <input
            type="number"
            min={1000}
            max={30000}
            value={maxTextFieldChars}
            onChange={(e) => setMaxTextFieldChars(Number(e.target.value))}
            className="w-24 rounded border border-gray-600 bg-gray-700 px-3 py-1.5 text-sm text-gray-200 focus:border-blue-500 focus:outline-none"
          />
          <span className="text-sm text-gray-400">characters [1000 to 30000]</span>
        </Row>

        <div className="border-t border-gray-700" />

        {/* Pause on focus lost */}
        <Row label="pause on focus lost">
          <Toggle checked={pauseOnFocusLost} onChange={setPauseOnFocusLost} />
        </Row>
      </div>
    </div>
  );
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex items-center justify-end gap-4">
      <span className="text-sm text-gray-300 text-right min-w-[200px]">{label}</span>
      <div className="flex items-center gap-2">{children}</div>
    </div>
  );
}

function Toggle({ checked, onChange }: { checked: boolean; onChange: (v: boolean) => void }) {
  return (
    <button
      role="switch"
      aria-checked={checked}
      onClick={() => onChange(!checked)}
      className={`relative inline-flex h-6 w-11 items-center rounded-full transition ${
        checked ? 'bg-green-600' : 'bg-gray-600'
      }`}
    >
      <span
        className={`inline-block h-4 w-4 transform rounded-full bg-white transition ${
          checked ? 'translate-x-6' : 'translate-x-1'
        }`}
      />
    </button>
  );
}
