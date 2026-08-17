/**
 * DEMO-ONLY role switcher: there is no real login/session in this POC. The
 * "role" is just a value the user picks in the sidebar, persisted to
 * localStorage and sent as the X-Demo-Role header on every API call, so the
 * backend can show the access-control pattern (see DemoRoleAccessFilter)
 * without building out real authentication.
 */
export type DemoRole = 'AGENT' | 'ADMIN';

const STORAGE_KEY = 'demoRole';

export function getDemoRole(): DemoRole {
  return localStorage.getItem(STORAGE_KEY) === 'ADMIN' ? 'ADMIN' : 'AGENT';
}

export function setDemoRole(role: DemoRole) {
  localStorage.setItem(STORAGE_KEY, role);
}
