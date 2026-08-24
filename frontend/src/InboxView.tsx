import { useEffect, useMemo, useState } from 'react';
import type { FormEvent } from 'react';
import { api } from './api';
import type { Category, Email, MailAccount, SendEmailRequest } from './types';
import { CategoryBadge, NegativeSentimentBadge, SpamBadge } from './Badge';

// NLP spamScore is a suggestion, not a verdict - only surface it above this likelihood.
const SPAM_SUGGESTION_THRESHOLD = 0.5;

function isSpamSuggested(email: Email): boolean {
  return (
    !email.spam &&
    !email.spamSuggestionDismissed &&
    email.spamScore != null &&
    email.spamScore >= SPAM_SUGGESTION_THRESHOLD
  );
}

function SpamSuggestionChip({
  email,
  onConfirm,
  onDismiss,
}: {
  email: Email;
  onConfirm: () => void;
  onDismiss: () => void;
}) {
  return (
    <span className="spam-suggestion" onClick={(e) => e.stopPropagation()}>
      Likely spam {Math.round((email.spamScore ?? 0) * 100)}%
      <button type="button" className="spam-suggestion-link" onClick={onConfirm}>
        Confirm
      </button>
      <button type="button" className="spam-suggestion-link" onClick={onDismiss}>
        Dismiss
      </button>
    </span>
  );
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString();
}

function formatBytes(bytes: number): string {
  if (bytes <= 0) return 'unknown size';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

// Matches email.greenmail.test-account in application.yml - the CRM's own address,
// excluded from auto-populated Reply All recipients.
const AGENT_ADDRESS = 'claims-inbox@uniqa-poc.local';

function splitAddresses(raw: string | null): string[] {
  return raw ? raw.split(',').map((a) => a.trim()).filter(Boolean) : [];
}

function withPrefix(subject: string, prefix: 'Re' | 'Fwd'): string {
  return new RegExp(`^${prefix}:\\s*`, 'i').test(subject) ? subject : `${prefix}: ${subject}`;
}

function quoteBody(email: Email): string {
  const quoted = email.body
    .split('\n')
    .map((line) => `> ${line}`)
    .join('\n');
  return `\n\nOn ${formatDate(email.receivedAt)}, ${email.fromAddress} wrote:\n${quoted}`;
}

function replyAllCc(email: Email): string[] {
  const exclude = new Set([AGENT_ADDRESS, email.fromAddress]);
  const seen = new Set(exclude);
  const result: string[] = [];
  for (const addr of [...splitAddresses(email.toAddresses), ...splitAddresses(email.ccAddresses)]) {
    if (!seen.has(addr)) {
      seen.add(addr);
      result.push(addr);
    }
  }
  return result;
}

const POLL_INTERVAL_MS = 5000;
const PAGE_SIZE = 15;

// Spam is a cross-cutting flag, not a real Category row - this pseudo-selection value
// filters the already-fetched "All" list client-side rather than hitting a category endpoint.
const SPAM_FILTER = 'SPAM';

export function InboxView() {
  const [emails, setEmails] = useState<Email[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [mailAccounts, setMailAccounts] = useState<MailAccount[]>([]);
  const [selectedCategory, setSelectedCategory] = useState<string>('ALL');
  const [selectedEmailId, setSelectedEmailId] = useState<number | null>(null);
  const [page, setPage] = useState(1);
  const [search, setSearch] = useState('');
  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  async function loadEmails(silent = false) {
    if (!silent) setLoading(true);
    setError(null);
    try {
      const data =
        selectedCategory === 'ALL' || selectedCategory === SPAM_FILTER
          ? await api.listEmails()
          : await api.listEmailsByCategory(selectedCategory);
      setEmails(data);
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      if (!silent) setLoading(false);
    }
  }

  useEffect(() => {
    api.listCategories().then(setCategories).catch(() => {});
    api.listMailAccounts().then(setMailAccounts).catch(() => {});
  }, []);

  useEffect(() => {
    loadEmails();
    const id = setInterval(() => loadEmails(true), POLL_INTERVAL_MS);
    return () => clearInterval(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedCategory]);

  async function handleOverride(id: number, category: string) {
    const previous = emails;
    setEmails((cur) => cur.map((e) => (e.id === id ? { ...e, finalCategory: category } : e)));
    try {
      await api.overrideCategory(id, category);
    } catch (e) {
      setEmails(previous);
      setError(e instanceof Error ? e.message : String(e));
    }
  }

  async function handleToggleImportant(id: number, important: boolean) {
    const previous = emails;
    setEmails((cur) => cur.map((e) => (e.id === id ? { ...e, important } : e)));
    try {
      await api.setEmailImportant(id, important);
    } catch (e) {
      setEmails(previous);
      setError(e instanceof Error ? e.message : String(e));
    }
  }

  async function handleToggleSpam(id: number, spam: boolean) {
    const previous = emails;
    setEmails((cur) =>
      cur.map((e) => (e.id === id ? { ...e, spam, spamSuggestionDismissed: true } : e)),
    );
    try {
      await api.setEmailSpam(id, spam);
    } catch (e) {
      setEmails(previous);
      setError(e instanceof Error ? e.message : String(e));
    }
  }

  async function handleDismissSpamSuggestion(id: number) {
    const previous = emails;
    setEmails((cur) => cur.map((e) => (e.id === id ? { ...e, spamSuggestionDismissed: true } : e)));
    try {
      await api.dismissSpamSuggestion(id);
    } catch (e) {
      setEmails(previous);
      setError(e instanceof Error ? e.message : String(e));
    }
  }

  async function handleArchive(id: number) {
    const previous = emails;
    setEmails((cur) => cur.filter((e) => e.id !== id));
    setSelectedEmailId((cur) => (cur === id ? null : cur));
    try {
      await api.setEmailArchived(id, true);
    } catch (e) {
      setEmails(previous);
      setError(e instanceof Error ? e.message : String(e));
    }
  }

  function handleSelectEmail(id: number) {
    setSelectedEmailId(id);
  }

  function selectCategory(name: string) {
    setSelectedCategory(name);
    setSelectedEmailId(null);
    setPage(1);
    setSelected(new Set());
  }

  function toggleSelect(id: number) {
    setSelected((cur) => {
      const next = new Set(cur);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  function toggleSelectAllOnPage(ids: number[], checked: boolean) {
    setSelected((cur) => {
      const next = new Set(cur);
      for (const id of ids) {
        if (checked) next.add(id);
        else next.delete(id);
      }
      return next;
    });
  }

  async function runBulk(ids: number[], apply: (id: number) => Promise<unknown>, optimistic: (cur: Email[]) => Email[]) {
    const previous = emails;
    setEmails(optimistic);
    setSelected(new Set());
    try {
      await Promise.all(ids.map(apply));
    } catch (e) {
      setEmails(previous);
      setError(e instanceof Error ? e.message : String(e));
    }
  }

  function handleBulkArchive() {
    const ids = [...selected];
    runBulk(
      ids,
      (id) => api.setEmailArchived(id, true),
      (cur) => cur.filter((e) => !ids.includes(e.id)),
    );
  }

  function handleBulkCategory(category: string) {
    const ids = [...selected];
    runBulk(
      ids,
      (id) => api.overrideCategory(id, category),
      (cur) => cur.map((e) => (ids.includes(e.id) ? { ...e, finalCategory: category } : e)),
    );
  }

  const sorted = useMemo(
    () => [...emails].sort((a, b) => b.receivedAt.localeCompare(a.receivedAt)),
    [emails],
  );

  const categoryScoped = useMemo(
    () => (selectedCategory === SPAM_FILTER ? sorted.filter((e) => e.spam) : sorted),
    [sorted, selectedCategory],
  );

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    const from = dateFrom ? new Date(`${dateFrom}T00:00:00`) : null;
    const to = dateTo ? new Date(`${dateTo}T23:59:59.999`) : null;
    return categoryScoped.filter((e) => {
      if (q) {
        const matchesText =
          e.subject.toLowerCase().includes(q) ||
          e.fromAddress.toLowerCase().includes(q) ||
          e.body.toLowerCase().includes(q);
        if (!matchesText) return false;
      }
      const receivedAt = new Date(e.receivedAt);
      if (from && receivedAt < from) return false;
      if (to && receivedAt > to) return false;
      return true;
    });
  }, [categoryScoped, search, dateFrom, dateTo]);

  const hasActiveFilters = search.trim() !== '' || dateFrom !== '' || dateTo !== '';

  function clearFilters() {
    setSearch('');
    setDateFrom('');
    setDateTo('');
    setPage(1);
  }

  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  // Clamp rather than reset on every render: a background poll shrinking the
  // list (e.g. after a delete) shouldn't yank the user back to page 1.
  const currentPage = Math.min(page, totalPages);
  const pageStart = (currentPage - 1) * PAGE_SIZE;
  const pageEmails = filtered.slice(pageStart, pageStart + PAGE_SIZE);
  const pageIds = pageEmails.map((e) => e.id);
  const allOnPageSelected = pageEmails.length > 0 && pageIds.every((id) => selected.has(id));

  const selectedEmail = selectedEmailId != null ? emails.find((e) => e.id === selectedEmailId) ?? null : null;

  return (
    <section className="inbox-columns">
      <CategoryNav
        categories={categories}
        selected={selectedCategory}
        onSelect={selectCategory}
      />

      <div className="inbox-content">
        {selectedEmail ? (
          <EmailDetail
            email={selectedEmail}
            categories={categories}
            mailAccounts={mailAccounts}
            onBack={() => setSelectedEmailId(null)}
            onOverride={handleOverride}
            onArchive={handleArchive}
            onToggleImportant={handleToggleImportant}
            onToggleSpam={handleToggleSpam}
            onDismissSpamSuggestion={handleDismissSpamSuggestion}
            onSend={(req) => api.sendEmail(selectedEmail.id, req)}
          />
        ) : (
          <>
            <div className="toolbar">
              <h2 className="pane-title">
                {selectedCategory === 'ALL'
                  ? 'All emails'
                  : selectedCategory === SPAM_FILTER
                    ? 'Spam'
                    : selectedCategory}
              </h2>
              <span className="muted live-indicator">● Live — auto-refreshing</span>
            </div>
            <div className="toolbar filter-bar">
              <input
                type="search"
                className="search-input"
                placeholder="Search subject, sender, or body…"
                value={search}
                onChange={(e) => {
                  setSearch(e.target.value);
                  setPage(1);
                }}
              />
              <label className="date-filter-label">
                Received from
                <input
                  type="date"
                  value={dateFrom}
                  max={dateTo || undefined}
                  onChange={(e) => {
                    setDateFrom(e.target.value);
                    setPage(1);
                  }}
                />
              </label>
              <label className="date-filter-label">
                to
                <input
                  type="date"
                  value={dateTo}
                  min={dateFrom || undefined}
                  onChange={(e) => {
                    setDateTo(e.target.value);
                    setPage(1);
                  }}
                />
              </label>
              {hasActiveFilters && (
                <button type="button" className="secondary" onClick={clearFilters}>
                  Clear filters
                </button>
              )}
            </div>

            {selected.size > 0 && (
              <div className="bulk-bar">
                <span>{selected.size} selected</span>
                <select
                  defaultValue=""
                  onChange={(e) => {
                    if (e.target.value) handleBulkCategory(e.target.value);
                  }}
                >
                  <option value="" disabled>
                    Set category…
                  </option>
                  {categories.map((c) => (
                    <option key={c.id} value={c.name}>
                      {c.name}
                    </option>
                  ))}
                </select>
                <button className="danger" onClick={handleBulkArchive}>
                  Archive
                </button>
                <button className="secondary" onClick={() => setSelected(new Set())}>
                  Clear
                </button>
              </div>
            )}

            {error && <p className="error">{error}</p>}
            <div className="email-list-panel">
              {loading ? (
                <p className="muted">Loading…</p>
              ) : sorted.length === 0 ? (
                <p className="muted">No emails yet — new mail is picked up automatically.</p>
              ) : filtered.length === 0 ? (
                <p className="muted">No emails match your search.</p>
              ) : (
                <>
                  <table>
                    <thead>
                      <tr>
                        <th className="checkbox-cell">
                          <input
                            type="checkbox"
                            checked={allOnPageSelected}
                            onChange={(e) => toggleSelectAllOnPage(pageIds, e.target.checked)}
                          />
                        </th>
                        <th className="star-cell"></th>
                        <th>Received</th>
                        <th>From</th>
                        <th>Subject</th>
                        <th>Flags</th>
                        <th className="hidden-temp">NLP guess</th>
                        <th className="hidden-temp">Final category</th>
                        <th className="hidden-temp"></th>
                      </tr>
                    </thead>
                    <tbody>
                      {pageEmails.map((email) => (
                        <tr
                          key={email.id}
                          className={[
                            'clickable-row',
                            email.important ? 'important-row' : '',
                          ]
                            .filter(Boolean)
                            .join(' ')}
                          onClick={() => handleSelectEmail(email.id)}
                        >
                          <td className="checkbox-cell" onClick={(e) => e.stopPropagation()}>
                            <input
                              type="checkbox"
                              checked={selected.has(email.id)}
                              onChange={() => toggleSelect(email.id)}
                            />
                          </td>
                          <td
                            className="star-cell"
                            onClick={(e) => {
                              e.stopPropagation();
                              handleToggleImportant(email.id, !email.important);
                            }}
                          >
                            <span
                              className={email.important ? 'star-icon important' : 'star-icon'}
                              title={email.important ? 'Unmark as important' : 'Mark as important'}
                            >
                              {email.important ? '★' : '☆'}
                            </span>
                          </td>
                          <td className="nowrap">{formatDate(email.receivedAt)}</td>
                          <td>{email.fromAddress}</td>
                          <td className="subject-cell" title={email.body}>
                            {email.subject}
                          </td>
                          <td className="nowrap">
                            {email.spam ? (
                              <SpamBadge />
                            ) : isSpamSuggested(email) ? (
                              <SpamSuggestionChip
                                email={email}
                                onConfirm={() => handleToggleSpam(email.id, true)}
                                onDismiss={() => handleDismissSpamSuggestion(email.id)}
                              />
                            ) : null}
                          </td>
                          <td className="nowrap hidden-temp">
                            <CategoryBadge name={email.nlpCategory} />{' '}
                            <span className="muted">{Math.round(email.nlpConfidence * 100)}%</span>
                          </td>
                          <td className="hidden-temp">
                            <CategoryBadge name={email.finalCategory} />
                          </td>
                          <td className="actions-cell hidden-temp">
                            <button
                              className="secondary"
                              onClick={(e) => {
                                e.stopPropagation();
                                handleArchive(email.id);
                              }}
                            >
                              Archive
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                  <Pagination
                    page={currentPage}
                    totalPages={totalPages}
                    totalItems={sorted.length}
                    pageSize={PAGE_SIZE}
                    onPageChange={setPage}
                  />
                </>
              )}
            </div>
          </>
        )}
      </div>
    </section>
  );
}

function Pagination({
  page,
  totalPages,
  totalItems,
  pageSize,
  onPageChange,
}: {
  page: number;
  totalPages: number;
  totalItems: number;
  pageSize: number;
  onPageChange: (page: number) => void;
}) {
  if (totalPages <= 1) return null;

  const rangeStart = (page - 1) * pageSize + 1;
  const rangeEnd = Math.min(page * pageSize, totalItems);

  return (
    <div className="pagination">
      <span className="muted">
        Showing {rangeStart}–{rangeEnd} of {totalItems}
      </span>
      <div className="pagination-controls">
        <button disabled={page <= 1} onClick={() => onPageChange(page - 1)}>
          ← Previous
        </button>
        <span className="pagination-page">
          Page{' '}
          <input
            type="number"
            min={1}
            max={totalPages}
            value={page}
            onChange={(e) => {
              const next = Number(e.target.value);
              if (next >= 1 && next <= totalPages) onPageChange(next);
            }}
          />{' '}
          of {totalPages}
        </span>
        <button disabled={page >= totalPages} onClick={() => onPageChange(page + 1)}>
          Next →
        </button>
      </div>
    </div>
  );
}

function CategoryNav({
  categories,
  selected,
  onSelect,
}: {
  categories: Category[];
  selected: string;
  onSelect: (name: string) => void;
}) {
  return (
    <div className="category-nav-panel">
      <h2>Categories</h2>
      <ul className="category-nav">
        <li>
          <button
            className={selected === 'ALL' ? 'active' : ''}
            onClick={() => onSelect('ALL')}
          >
            All
          </button>
        </li>
        <li>
          <button
            className={selected === SPAM_FILTER ? 'active nav-spam' : 'nav-spam'}
            onClick={() => onSelect(SPAM_FILTER)}
            title="Emails a human confirmed or the NLP flagged as spam - never archived, always here"
          >
            ⚠ Spam
          </button>
        </li>
      </ul>
      <div className="category-nav-divider" />
      <ul className="category-nav">
        {categories.map((c) => (
          <li key={c.id}>
            <button
              className={selected === c.name ? 'active' : ''}
              onClick={() => onSelect(c.name)}
              title={c.description ?? undefined}
            >
              {c.name}
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}

function EmailDetail({
  email,
  categories,
  mailAccounts,
  onBack,
  onOverride,
  onArchive,
  onToggleImportant,
  onToggleSpam,
  onDismissSpamSuggestion,
  onSend,
}: {
  email: Email;
  categories: Category[];
  mailAccounts: MailAccount[];
  onBack: () => void;
  onOverride: (id: number, category: string) => void;
  onArchive: (id: number) => void;
  onToggleImportant: (id: number, important: boolean) => void;
  onToggleSpam: (id: number, spam: boolean) => void;
  onDismissSpamSuggestion: (id: number) => void;
  onSend: (req: SendEmailRequest) => Promise<void>;
}) {
  const [composeMode, setComposeMode] = useState<'reply' | 'replyAll' | 'forward' | null>(null);

  return (
    <div className="email-detail">
      <div className="email-detail-toolbar">
        <button className="back-link" onClick={onBack}>
          ← Back to list
        </button>
        <div className="email-detail-actions">
          <button onClick={() => setComposeMode('reply')}>Reply</button>
          <button className="secondary" onClick={() => setComposeMode('replyAll')}>
            Reply All
          </button>
          <button className="secondary" onClick={() => setComposeMode('forward')}>
            Forward
          </button>
          <button
            className={email.important ? 'secondary important-active' : 'secondary'}
            onClick={() => onToggleImportant(email.id, !email.important)}
          >
            {email.important ? '★ Important' : '☆ Mark important'}
          </button>
          <button
            className={email.spam ? 'secondary spam-active' : 'secondary'}
            onClick={() => onToggleSpam(email.id, !email.spam)}
          >
            {email.spam ? 'Not spam' : 'Mark as spam'}
          </button>
          <button className="danger" onClick={() => onArchive(email.id)}>
            Archive
          </button>
        </div>
      </div>

      <h2 className="email-detail-subject">
        {email.subject}{' '}
        {email.spam ? (
          <SpamBadge />
        ) : (
          isSpamSuggested(email) && (
            <SpamSuggestionChip
              email={email}
              onConfirm={() => onToggleSpam(email.id, true)}
              onDismiss={() => onDismissSpamSuggestion(email.id)}
            />
          )
        )}
      </h2>
      <dl className="email-detail-meta">
        <div>
          <dt>From</dt>
          <dd>{email.fromAddress}</dd>
        </div>
        {email.toAddresses && (
          <div>
            <dt>To</dt>
            <dd>{email.toAddresses}</dd>
          </div>
        )}
        {email.ccAddresses && (
          <div>
            <dt>Cc</dt>
            <dd>{email.ccAddresses}</dd>
          </div>
        )}
        {email.replyTo && (
          <div>
            <dt>Reply-To</dt>
            <dd>{email.replyTo}</dd>
          </div>
        )}
        <div>
          <dt>Received</dt>
          <dd>{formatDate(email.receivedAt)}</dd>
        </div>
        {email.inReplyTo && (
          <div>
            <dt>In reply to</dt>
            <dd className="condition-cell">{email.inReplyTo}</dd>
          </div>
        )}
        <div>
          <dt>NLP guess</dt>
          <dd>
            <CategoryBadge name={email.nlpCategory} />{' '}
            <span className="muted">{Math.round(email.nlpConfidence * 100)}%</span>
          </dd>
        </div>
        <div>
          <dt>Sentiment</dt>
          <dd>{email.negativeSentiment ? <NegativeSentimentBadge /> : '—'}</dd>
        </div>
        <div>
          <dt>Matched rule</dt>
          <dd>{email.matchedRuleId ?? '—'}</dd>
        </div>
        <div>
          <dt>Final category</dt>
          <dd>
            <select
              value={email.finalCategory}
              onChange={(e) => onOverride(email.id, e.target.value)}
            >
              {!categories.some((c) => c.name === email.finalCategory) && (
                <option value={email.finalCategory}>{email.finalCategory}</option>
              )}
              {categories.map((c) => (
                <option key={c.id} value={c.name}>
                  {c.name}
                </option>
              ))}
            </select>
          </dd>
        </div>
      </dl>

      {email.attachments.length > 0 && (
        <div className="email-attachments">
          <h3>Attachments</h3>
          <ul>
            {email.attachments.map((a, i) => (
              <li key={i}>
                {a.filename} — {formatBytes(a.sizeBytes)}
                {a.contentType && <span className="muted"> ({a.contentType})</span>}
              </li>
            ))}
          </ul>
        </div>
      )}

      <div className="email-detail-body">{email.body}</div>

      {composeMode && (
        <ComposePanel
          mode={composeMode}
          email={email}
          mailAccounts={mailAccounts}
          onSend={onSend}
          onCancel={() => setComposeMode(null)}
        />
      )}

      {email.rawHeaders && (
        <details className="raw-headers">
          <summary>Raw headers</summary>
          <pre>{email.rawHeaders}</pre>
        </details>
      )}
    </div>
  );
}

/**
 * Which configured send-capable account should be preselected: the one that
 * actually received this email, if it's allowed to send; otherwise the first
 * send-capable account, since most inboxes are receive-only aliases (see
 * DemoMailAccounts) and can't be a legitimate "From".
 */
function defaultFromAddress(email: Email, sendable: MailAccount[]): string {
  const receiving = sendable.find((a) => a.address === email.receivingAccount);
  return receiving?.address ?? sendable[0]?.address ?? '';
}

function ComposePanel({
  mode,
  email,
  mailAccounts,
  onSend,
  onCancel,
}: {
  mode: 'reply' | 'replyAll' | 'forward';
  email: Email;
  mailAccounts: MailAccount[];
  onSend: (req: SendEmailRequest) => Promise<void>;
  onCancel: () => void;
}) {
  const sendableAccounts = useMemo(() => mailAccounts.filter((a) => a.canSend), [mailAccounts]);

  const initial = useMemo(() => {
    const body = quoteBody(email);
    if (mode === 'forward') {
      return { to: '', cc: '', subject: withPrefix(email.subject, 'Fwd'), body };
    }
    return {
      to: email.fromAddress,
      cc: mode === 'replyAll' ? replyAllCc(email).join(', ') : '',
      subject: withPrefix(email.subject, 'Re'),
      body,
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mode, email.id]);

  const [from, setFrom] = useState(() => defaultFromAddress(email, sendableAccounts));
  const [to, setTo] = useState(initial.to);
  const [cc, setCc] = useState(initial.cc);
  const [subject, setSubject] = useState(initial.subject);
  const [body, setBody] = useState(initial.body);
  const [sending, setSending] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setSending(true);
    setError(null);
    try {
      await onSend({
        fromAddress: from,
        to: to.split(',').map((a) => a.trim()).filter(Boolean),
        cc: cc.split(',').map((a) => a.trim()).filter(Boolean),
        subject,
        body,
      });
      setSent(true);
    } catch (e2) {
      setError(e2 instanceof Error ? e2.message : String(e2));
    } finally {
      setSending(false);
    }
  }

  const title = mode === 'reply' ? 'Reply' : mode === 'replyAll' ? 'Reply All' : 'Forward';

  if (sent) {
    return (
      <div className="compose-panel">
        <p className="muted">✓ Sent.</p>
        <button type="button" className="secondary" onClick={onCancel}>
          Close
        </button>
      </div>
    );
  }

  return (
    <form className="compose-panel" onSubmit={handleSubmit}>
      <h3>{title}</h3>
      {error && <p className="error">{error}</p>}
      <label>
        From
        <select required value={from} onChange={(e) => setFrom(e.target.value)}>
          {sendableAccounts.length === 0 && <option value="">No send-capable account configured</option>}
          {sendableAccounts.map((a) => (
            <option key={a.id} value={a.address}>
              {a.displayName} ({a.address})
            </option>
          ))}
        </select>
      </label>
      <label>
        To
        <input required placeholder="comma-separated" value={to} onChange={(e) => setTo(e.target.value)} />
      </label>
      <label>
        Cc
        <input
          placeholder="comma-separated, optional"
          value={cc}
          onChange={(e) => setCc(e.target.value)}
        />
      </label>
      <label>
        Subject
        <input required value={subject} onChange={(e) => setSubject(e.target.value)} />
      </label>
      <label>
        Body
        <textarea required rows={10} value={body} onChange={(e) => setBody(e.target.value)} />
      </label>
      <div className="row">
        <button type="submit" disabled={sending || !from}>
          {sending ? 'Sending…' : 'Send'}
        </button>
        <button type="button" className="secondary" onClick={onCancel}>
          Cancel
        </button>
      </div>
    </form>
  );
}
