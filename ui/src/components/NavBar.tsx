import { NavLink, useLocation } from 'react-router-dom';

const tabs = [
  { to: '/debugger', label: 'DEBUGGER' },
  { to: '/metrics', label: 'METRICS' },
  { to: '/settings', label: 'SETTINGS' },
];

export function NavBar() {
  const location = useLocation();

  return (
    <nav className="flex items-center justify-between border-b border-gray-700 bg-gray-900 px-4 py-2">
      {/* Left: Back button */}
      <div className="flex w-40 items-center">
        {location.pathname !== '/debugger' && (
          <NavLink
            to="/debugger"
            className="text-xs text-gray-400 hover:text-gray-200 transition"
          >
            &lt; BACK
          </NavLink>
        )}
      </div>

      {/* Center: Title + Tabs */}
      <div className="flex items-center gap-6">
        <span className="text-sm font-bold tracking-wider text-amber-400">
          CAMEL BEE
        </span>
        <div className="flex gap-1">
          {tabs.map((tab) => (
            <NavLink
              key={tab.to}
              to={tab.to}
              className={({ isActive }) =>
                `px-3 py-1 text-xs font-medium rounded transition ${
                  isActive
                    ? 'bg-gray-700 text-white'
                    : 'text-gray-400 hover:text-gray-200 hover:bg-gray-800'
                }`
              }
            >
              {tab.label}
            </NavLink>
          ))}
        </div>
      </div>

      {/* Right: Version */}
      <div className="flex w-40 justify-end">
        <span className="text-xs text-gray-500">V 2.0.8</span>
      </div>
    </nav>
  );
}
