export type RuleField = 'SUBJECT' | 'BODY' | 'SUBJECT_OR_BODY';
export type RuleOperator =
  | 'CONTAINS'
  | 'NOT_CONTAINS'
  | 'EQUALS'
  | 'NOT_EQUALS'
  | 'STARTS_WITH'
  | 'NOT_STARTS_WITH'
  | 'ENDS_WITH'
  | 'NOT_ENDS_WITH'
  | 'IS_EMPTY'
  | 'IS_NOT_EMPTY'
  | 'MATCHES_REGEX';
/** Earlier stage always beats a later one, regardless of priority. */
export type RuleStage = 'CRITICAL' | 'STANDARD' | 'FALLBACK';
export const OPERATORS_WITHOUT_VALUE: RuleOperator[] = ['IS_EMPTY', 'IS_NOT_EMPTY'];

export interface Category {
  id: number;
  name: string;
  description: string | null;
}

export interface CategoryRequest {
  name: string;
  description: string | null;
}

/** One (field, operator, values) test. Multiple values within a condition are OR'd. */
export interface RuleCondition {
  field: RuleField;
  operator: RuleOperator;
  values: string[];
}

export interface Rule {
  id: number;
  name: string;
  description: string | null;
  /** Conditions on a rule are AND'd together. */
  conditions: RuleCondition[];
  requireNegativeSentiment: boolean;
  targetCategoryName: string;
  stage: RuleStage;
  priority: number;
  active: boolean;
}

export type RuleRequest = Omit<Rule, 'id'>;

export interface EmailAttachment {
  filename: string;
  sizeBytes: number;
  contentType: string | null;
}

export interface Email {
  id: number;
  messageId: string;
  fromAddress: string;
  subject: string;
  body: string;
  receivedAt: string;
  hasAttachment: boolean;
  toAddresses: string | null;
  ccAddresses: string | null;
  replyTo: string | null;
  inReplyTo: string | null;
  referencesHeader: string | null;
  rawHeaders: string | null;
  attachments: EmailAttachment[];
  nlpCategory: string;
  nlpConfidence: number;
  negativeSentiment: boolean;
  suggestedCategory: string | null;
  matchedRuleId: number | null;
  finalCategory: string;
  processed: boolean;
  read: boolean;
  archived: boolean;
  createdAt: string;
}

export interface SendEmailRequest {
  to: string[];
  cc: string[];
  subject: string;
  body: string;
}
