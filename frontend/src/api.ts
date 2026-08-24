import type { Category, CategoryRequest, Email, MailAccount, Rule, RuleRequest, SendEmailRequest } from './types';
import { getDemoRole } from './demoRole';

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, {
    headers: { 'Content-Type': 'application/json', 'X-Demo-Role': getDemoRole() },
    ...init,
  });
  if (!res.ok) {
    const text = await res.text().catch(() => '');
    throw new Error(`${init?.method ?? 'GET'} ${path} failed: ${res.status} ${text}`);
  }
  if (res.status === 204 || res.headers.get('content-length') === '0') {
    return undefined as T;
  }
  return res.json() as Promise<T>;
}

export const api = {
  listEmails: () => request<Email[]>('/api/emails'),
  listEmailsByCategory: (category: string) =>
    request<Email[]>(`/api/emails/category/${encodeURIComponent(category)}`),
  fetchNow: () => request<Email[]>('/api/emails/fetch-now', { method: 'POST' }),
  overrideCategory: (id: number, category: string) =>
    request<Email>(`/api/emails/${id}/category`, {
      method: 'PUT',
      body: JSON.stringify({ category }),
    }),
  setEmailImportant: (id: number, important: boolean) =>
    request<Email>(`/api/emails/${id}/important`, {
      method: 'PUT',
      body: JSON.stringify({ important }),
    }),
  setEmailSpam: (id: number, spam: boolean) =>
    request<Email>(`/api/emails/${id}/spam`, { method: 'PUT', body: JSON.stringify({ spam }) }),
  dismissSpamSuggestion: (id: number) =>
    request<Email>(`/api/emails/${id}/spam-suggestion-dismiss`, { method: 'PUT' }),
  setEmailArchived: (id: number, archived: boolean) =>
    request<Email>(`/api/emails/${id}/archived`, {
      method: 'PUT',
      body: JSON.stringify({ archived }),
    }),
  sendEmail: (id: number, req: SendEmailRequest) =>
    request<void>(`/api/emails/${id}/send`, { method: 'POST', body: JSON.stringify(req) }),

  listRules: () => request<Rule[]>('/api/rules'),
  createRule: (rule: RuleRequest) =>
    request<Rule>('/api/rules', { method: 'POST', body: JSON.stringify(rule) }),
  updateRule: (id: number, rule: RuleRequest) =>
    request<Rule>(`/api/rules/${id}`, { method: 'PUT', body: JSON.stringify(rule) }),
  deleteRule: (id: number) => request<void>(`/api/rules/${id}`, { method: 'DELETE' }),

  listMailAccounts: () => request<MailAccount[]>('/api/mail-accounts'),

  listCategories: () => request<Category[]>('/api/categories'),
  createCategory: (category: CategoryRequest) =>
    request<Category>('/api/categories', { method: 'POST', body: JSON.stringify(category) }),
  deleteCategory: (id: number) => request<void>(`/api/categories/${id}`, { method: 'DELETE' }),
};
