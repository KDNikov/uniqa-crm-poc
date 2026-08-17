// Validated categorical palette (light mode) - passes the dataviz skill's
// six-check validator in this fixed order (worst adjacent CVD ΔE 9.1, worst
// adjacent normal-vision ΔE 19.6). Reused for both badges and the dashboard's
// category bars so the same category always reads as the same color everywhere.
const CATEGORY_PALETTE = [
  '#2a78d6', // blue
  '#eb6834', // orange
  '#1baf7a', // aqua
  '#eda100', // yellow
  '#e87ba4', // magenta
  '#008300', // green
  '#4a3aa7', // violet
  '#e34948', // red
];

// A semi-transparent background (rather than a pre-mixed light pastel) blends
// with whatever surface it sits on, so every badge auto-adapts to light/dark
// theme without needing separate dark-mode color pairs.
function alphaBg(hex: string): string {
  return `${hex}26`;
}

/** Color follows the entity (hashed from its name), never its rank - so a
 * category keeps the same color even if counts/ordering shift elsewhere. */
export function categoryColor(name: string): { bg: string; fg: string } {
  let hash = 0;
  for (let i = 0; i < name.length; i++) hash = (hash * 31 + name.charCodeAt(i)) >>> 0;
  const fg = CATEGORY_PALETTE[hash % CATEGORY_PALETTE.length];
  return { bg: alphaBg(fg), fg };
}

export function CategoryBadge({ name }: { name: string }) {
  const { bg, fg } = categoryColor(name);
  return (
    <span className="badge" style={{ background: bg, color: fg }}>
      {name}
    </span>
  );
}

const STAGE_FG: Record<string, string> = {
  CRITICAL: '#dc2626',
  STANDARD: '#2563eb',
  FALLBACK: '#64748b',
};

export function StageBadge({ stage }: { stage: string }) {
  const fg = STAGE_FG[stage] ?? STAGE_FG.STANDARD;
  return (
    <span className="badge" style={{ background: alphaBg(fg), color: fg }}>
      {stage}
    </span>
  );
}

export function StatusBadge({ active }: { active: boolean }) {
  const fg = active ? '#059669' : '#64748b';
  return (
    <span className="badge" style={{ background: alphaBg(fg), color: fg }}>
      {active ? 'Active' : 'Inactive'}
    </span>
  );
}

export function NegativeSentimentBadge() {
  const fg = '#dc2626';
  return (
    <span className="badge" style={{ background: alphaBg(fg), color: fg }}>
      Negative
    </span>
  );
}
