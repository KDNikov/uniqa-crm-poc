import { useEffect, useMemo, useState } from 'react';
import { api } from './api';
import type { Email } from './types';
import { CategoryBadge, NegativeSentimentBadge } from './Badge';

function overrideLabel(email: Email): string {
  if (email.matchedRuleId == null) return '—';
  return email.finalCategory === email.nlpCategory
    ? 'no (rule agreed)'
    : `→ overridden to ${email.finalCategory}`;
}

export function NlpView() {
  const [emails, setEmails] = useState<Email[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  async function load() {
    setLoading(true);
    setError(null);
    try {
      setEmails(await api.listEmails());
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    load();
  }, []);

  const sorted = useMemo(
    () => [...emails].sort((a, b) => b.receivedAt.localeCompare(a.receivedAt)),
    [emails],
  );

  return (
    <section>
      <div className="toolbar">
        <h2 className="pane-title">NLP insights</h2>
        <button onClick={load} disabled={loading}>
          Refresh
        </button>
      </div>
      <p className="muted">
        Raw output of the local OpenNLP categorizer per email, and whether the rule engine
        overrode its guess for the final category.
      </p>

      {error && <p className="error">{error}</p>}
      <div className="email-list-panel">
        {loading ? (
          <p className="muted">Loading…</p>
        ) : sorted.length === 0 ? (
          <p className="muted">No emails yet.</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>Subject</th>
                <th>From</th>
                <th>NLP guess</th>
                <th>Confidence</th>
                <th>Sentiment</th>
                <th>Matched rule</th>
                <th>Final category</th>
                <th>Rule override?</th>
              </tr>
            </thead>
            <tbody>
              {sorted.map((email) => (
                <tr key={email.id}>
                  <td className="subject-cell" title={email.body}>
                    {email.subject}
                  </td>
                  <td>{email.fromAddress}</td>
                  <td>
                    <CategoryBadge name={email.nlpCategory} />
                  </td>
                  <td>{Math.round(email.nlpConfidence * 100)}%</td>
                  <td>{email.negativeSentiment ? <NegativeSentimentBadge /> : '—'}</td>
                  <td>{email.matchedRuleId ?? '—'}</td>
                  <td>
                    <CategoryBadge name={email.finalCategory} />
                  </td>
                  <td>{overrideLabel(email)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </section>
  );
}
