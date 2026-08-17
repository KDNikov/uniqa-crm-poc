import { useEffect, useMemo, useState } from 'react';
import type { FormEvent } from 'react';
import { api } from './api';
import type { Category, Rule, RuleCondition, RuleField, RuleOperator, RuleRequest, RuleStage } from './types';
import { OPERATORS_WITHOUT_VALUE } from './types';
import { CategoryBadge, StageBadge, StatusBadge } from './Badge';

const RULE_FIELDS: RuleField[] = ['SUBJECT', 'BODY', 'SUBJECT_OR_BODY'];
const RULE_OPERATORS: RuleOperator[] = [
  'CONTAINS',
  'NOT_CONTAINS',
  'EQUALS',
  'NOT_EQUALS',
  'STARTS_WITH',
  'NOT_STARTS_WITH',
  'ENDS_WITH',
  'NOT_ENDS_WITH',
  'IS_EMPTY',
  'IS_NOT_EMPTY',
  'MATCHES_REGEX',
];
// Earlier stage always outranks a later one, regardless of priority - mirrors RuleStage.getWeight() on the backend.
const RULE_STAGES: RuleStage[] = ['CRITICAL', 'STANDARD', 'FALLBACK'];
const STAGE_WEIGHT: Record<RuleStage, number> = { CRITICAL: 2, STANDARD: 1, FALLBACK: 0 };

const emptyCondition = (): RuleCondition => ({ field: 'BODY', operator: 'CONTAINS', values: [''] });

const emptyForm = (defaultCategory: string): RuleRequest => ({
  name: '',
  description: '',
  conditions: [emptyCondition()],
  requireNegativeSentiment: false,
  targetCategoryName: defaultCategory,
  stage: 'STANDARD',
  priority: 0,
  active: true,
});

export function RulesView() {
  const [rules, setRules] = useState<Rule[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [form, setForm] = useState<RuleRequest>(emptyForm(''));
  const [saving, setSaving] = useState(false);

  async function loadAll() {
    setLoading(true);
    setError(null);
    try {
      const [ruleData, categoryData] = await Promise.all([api.listRules(), api.listCategories()]);
      setRules(ruleData);
      setCategories(categoryData);
      setForm((f) => (f.targetCategoryName ? f : { ...f, targetCategoryName: categoryData[0]?.name ?? '' }));
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    loadAll();
  }, []);

  const sortedRules = useMemo(
    () =>
      [...rules].sort(
        (a, b) =>
          STAGE_WEIGHT[b.stage] - STAGE_WEIGHT[a.stage] || b.priority - a.priority || a.id - b.id,
      ),
    [rules],
  );

  function startEdit(rule: Rule) {
    setEditingId(rule.id);
    setForm({
      name: rule.name,
      description: rule.description ?? '',
      conditions: rule.conditions.map((c) => ({ ...c, values: [...c.values] })),
      requireNegativeSentiment: rule.requireNegativeSentiment,
      targetCategoryName: rule.targetCategoryName,
      stage: rule.stage,
      priority: rule.priority,
      active: rule.active,
    });
  }

  function updateCondition(index: number, patch: Partial<RuleCondition>) {
    setForm((f) => ({
      ...f,
      conditions: f.conditions.map((c, i) => (i === index ? { ...c, ...patch } : c)),
    }));
  }

  function addCondition() {
    setForm((f) => ({ ...f, conditions: [...f.conditions, emptyCondition()] }));
  }

  function removeCondition(index: number) {
    setForm((f) => ({ ...f, conditions: f.conditions.filter((_, i) => i !== index) }));
  }

  function cancelEdit() {
    setEditingId(null);
    setForm(emptyForm(categories[0]?.name ?? ''));
  }

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setSaving(true);
    setError(null);
    try {
      if (editingId != null) {
        const updated = await api.updateRule(editingId, form);
        setRules((cur) => cur.map((r) => (r.id === editingId ? updated : r)));
      } else {
        const created = await api.createRule(form);
        setRules((cur) => [...cur, created]);
      }
      cancelEdit();
    } catch (e2) {
      setError(e2 instanceof Error ? e2.message : String(e2));
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete(id: number) {
    setError(null);
    try {
      await api.deleteRule(id);
      setRules((cur) => cur.filter((r) => r.id !== id));
      if (editingId === id) cancelEdit();
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }

  return (
    <section>
      <div className="columns">
        <div>
          <h2>Rules</h2>
          {error && <p className="error">{error}</p>}
          {loading ? (
            <p className="muted">Loading…</p>
          ) : sortedRules.length === 0 ? (
            <p className="muted">No rules yet — every email falls back to the raw NLP guess.</p>
          ) : (
            <table>
              <thead>
                <tr>
                  <th>Stage</th>
                  <th>Priority</th>
                  <th>Name</th>
                  <th>Condition</th>
                  <th>Target category</th>
                  <th>Active</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {sortedRules.map((rule) => (
                  <tr key={rule.id} className={rule.id === editingId ? 'editing' : ''}>
                    <td>
                      <StageBadge stage={rule.stage} />
                    </td>
                    <td>{rule.priority}</td>
                    <td>{rule.name}</td>
                    <td className="condition-cell">
                      {rule.conditions
                        .map(
                          (c) =>
                            `${c.field} ${c.operator}` +
                            (OPERATORS_WITHOUT_VALUE.includes(c.operator)
                              ? ''
                              : ` "${c.values.join(', ')}"`),
                        )
                        .join(' AND ')}
                      {rule.requireNegativeSentiment && ' + negative sentiment'}
                    </td>
                    <td>
                      <CategoryBadge name={rule.targetCategoryName} />
                    </td>
                    <td>
                      <StatusBadge active={rule.active} />
                    </td>
                    <td className="actions-cell">
                      <button className="secondary" onClick={() => startEdit(rule)}>
                        Edit
                      </button>
                      <button className="danger" onClick={() => handleDelete(rule.id)}>
                        Delete
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}

          <h3>{editingId != null ? `Edit rule #${editingId}` : 'New rule'}</h3>
          <form className="rule-form" onSubmit={handleSubmit}>
            <label>
              Name
              <input
                required
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
              />
            </label>
            <label>
              Description
              <input
                value={form.description ?? ''}
                onChange={(e) => setForm({ ...form, description: e.target.value })}
              />
            </label>
            <p className="muted condition-hint">
              Every condition below must match (AND). Separate multiple values with a comma to
              match any of them (OR) — e.g. Body STARTS_WITH "Dear, Hello".
            </p>
            {form.conditions.map((condition, idx) => (
              <div className="row condition-row" key={idx}>
                <label>
                  Field
                  <select
                    value={condition.field}
                    onChange={(e) => updateCondition(idx, { field: e.target.value as RuleField })}
                  >
                    {RULE_FIELDS.map((f) => (
                      <option key={f} value={f}>
                        {f}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Operator
                  <select
                    value={condition.operator}
                    onChange={(e) => {
                      const operator = e.target.value as RuleOperator;
                      updateCondition(idx, {
                        operator,
                        values: OPERATORS_WITHOUT_VALUE.includes(operator) ? [''] : condition.values,
                      });
                    }}
                  >
                    {RULE_OPERATORS.map((op) => (
                      <option key={op} value={op}>
                        {op}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  Value(s)
                  <input
                    required={!OPERATORS_WITHOUT_VALUE.includes(condition.operator)}
                    disabled={OPERATORS_WITHOUT_VALUE.includes(condition.operator)}
                    placeholder={
                      condition.operator === 'MATCHES_REGEX'
                        ? 'regex (must match the whole field)'
                        : OPERATORS_WITHOUT_VALUE.includes(condition.operator)
                          ? 'not needed for this operator'
                          : 'value1, value2, ...'
                    }
                    value={
                      OPERATORS_WITHOUT_VALUE.includes(condition.operator)
                        ? ''
                        // join(',') exactly mirrors split(',') below - joining with ', ' would
                        // inject a space the user didn't type, desyncing the cursor after every comma.
                        : condition.values.join(',')
                    }
                    onChange={(e) => updateCondition(idx, { values: e.target.value.split(',') })}
                    onBlur={() =>
                      updateCondition(idx, { values: condition.values.map((v) => v.trim()) })
                    }
                  />
                </label>
                {form.conditions.length > 1 && (
                  <button type="button" className="danger" onClick={() => removeCondition(idx)}>
                    Remove
                  </button>
                )}
              </div>
            ))}
            <div className="row">
              <button type="button" className="secondary" onClick={addCondition}>
                + Add condition
              </button>
            </div>
            <div className="row">
              <label className="checkbox">
                <input
                  type="checkbox"
                  checked={form.requireNegativeSentiment}
                  onChange={(e) =>
                    setForm({ ...form, requireNegativeSentiment: e.target.checked })
                  }
                />
                Require negative sentiment
              </label>
              <label className="checkbox">
                <input
                  type="checkbox"
                  checked={form.active}
                  onChange={(e) => setForm({ ...form, active: e.target.checked })}
                />
                Active
              </label>
            </div>
            <div className="row">
              <label>
                Target category
                <select
                  required
                  value={form.targetCategoryName}
                  onChange={(e) => setForm({ ...form, targetCategoryName: e.target.value })}
                >
                  {categories.map((c) => (
                    <option key={c.id} value={c.name}>
                      {c.name}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Stage
                <select
                  value={form.stage}
                  onChange={(e) => setForm({ ...form, stage: e.target.value as RuleStage })}
                >
                  {RULE_STAGES.map((s) => (
                    <option key={s} value={s}>
                      {s}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Priority
                <input
                  type="number"
                  value={form.priority}
                  onChange={(e) => setForm({ ...form, priority: Number(e.target.value) })}
                />
              </label>
            </div>
            <div className="row">
              <button type="submit" disabled={saving || categories.length === 0}>
                {editingId != null ? 'Save changes' : 'Create rule'}
              </button>
              {editingId != null && (
                <button type="button" className="secondary" onClick={cancelEdit}>
                  Cancel
                </button>
              )}
            </div>
          </form>
        </div>

        <CategoriesPanel
          categories={categories}
          onChanged={setCategories}
          setError={setError}
        />
      </div>
    </section>
  );
}

function CategoriesPanel({
  categories,
  onChanged,
  setError,
}: {
  categories: Category[];
  onChanged: (categories: Category[]) => void;
  setError: (message: string | null) => void;
}) {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [saving, setSaving] = useState(false);

  async function handleAdd(e: FormEvent) {
    e.preventDefault();
    setSaving(true);
    setError(null);
    try {
      const created = await api.createCategory({ name, description: description || null });
      onChanged([...categories, created]);
      setName('');
      setDescription('');
    } catch (e2) {
      setError(e2 instanceof Error ? e2.message : String(e2));
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete(id: number) {
    setError(null);
    try {
      await api.deleteCategory(id);
      onChanged(categories.filter((c) => c.id !== id));
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    }
  }

  return (
    <div className="categories-panel">
      <h2>Categories</h2>
      <ul className="category-list">
        {categories.map((c) => (
          <li key={c.id}>
            <div>
              <strong>{c.name}</strong>
              {c.description && <span className="muted"> — {c.description}</span>}
            </div>
            <button className="danger" onClick={() => handleDelete(c.id)}>
              Delete
            </button>
          </li>
        ))}
      </ul>
      <form className="category-form" onSubmit={handleAdd}>
        <label>
          Name
          <input required value={name} onChange={(e) => setName(e.target.value)} />
        </label>
        <label>
          Description
          <input value={description} onChange={(e) => setDescription(e.target.value)} />
        </label>
        <button type="submit" disabled={saving}>
          Add category
        </button>
      </form>
    </div>
  );
}
