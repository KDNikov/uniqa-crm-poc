import { useState } from 'react';
import './App.css';
import { InboxView } from './InboxView';
import { NlpView } from './NlpView';
import { RulesView } from './RulesView';
import type { DemoRole } from './demoRole';
import { getDemoRole, setDemoRole } from './demoRole';
import type { Theme } from './theme';
import { getTheme, setTheme } from './theme';

type Tab = 'inbox' | 'nlp' | 'rules';

const NAV_ITEMS: { key: Tab; label: string; adminOnly?: boolean }[] = [
  { key: 'inbox', label: 'Inbox' },
  { key: 'nlp', label: 'NLP', adminOnly: true },
  { key: 'rules', label: 'Rules', adminOnly: true },
];

function App() {
  const [tab, setTab] = useState<Tab>('inbox');
  const [role, setRole] = useState<DemoRole>(getDemoRole());
  const [theme, setThemeState] = useState<Theme>(getTheme());

  function handleThemeChange(next: Theme) {
    setThemeState(next);
    setTheme(next);
  }

  function handleRoleChange(next: DemoRole) {
    setRole(next);
    setDemoRole(next);
    const currentItem = NAV_ITEMS.find((item) => item.key === tab);
    if (currentItem?.adminOnly && next !== 'ADMIN') {
      setTab('inbox');
    }
  }

  const visibleItems = NAV_ITEMS.filter((item) => !item.adminOnly || role === 'ADMIN');

  return (
    <div className="app">
      <nav className="sidebar">
        <div className="sidebar-brand">
          <span className="sidebar-brand-mark">TB</span>
          <div>
            <h1>TeamBox</h1>
            <p>UNIQA Email CRM</p>
          </div>
        </div>
        {visibleItems.map((item) => (
          <button
            key={item.key}
            className={tab === item.key ? 'active' : ''}
            onClick={() => setTab(item.key)}
          >
            {item.label}
          </button>
        ))}
        <div className="sidebar-footer">
          <label className="role-switcher">
            Theme
            <select value={theme} onChange={(e) => handleThemeChange(e.target.value as Theme)}>
              <option value="system">System</option>
              <option value="light">Light</option>
              <option value="dark">Dark</option>
            </select>
          </label>
          <label className="role-switcher">
            Demo role
            <select value={role} onChange={(e) => handleRoleChange(e.target.value as DemoRole)}>
              <option value="AGENT">Agent</option>
              <option value="ADMIN">Admin</option>
            </select>
          </label>
        </div>
      </nav>

      <main className="main-content">
        {tab === 'inbox' && <InboxView />}
        {tab === 'nlp' && role === 'ADMIN' && <NlpView />}
        {tab === 'rules' && role === 'ADMIN' && <RulesView />}
      </main>
    </div>
  );
}

export default App;
