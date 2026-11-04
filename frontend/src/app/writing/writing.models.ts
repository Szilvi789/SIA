import { ModelExecution, ModelSelection, ResolvedModelMetadata } from '../core/models';

export type WritingWorkflow = 'GENERAL' | 'EMAIL' | 'TRANSLATION' | 'CV' | 'COVER_LETTER' | 'JOB_APPLICATION' | 'CUSTOM_DOCUMENT';
export type OutputLanguage = 'EN' | 'DE' | 'HU';
export type InputLanguage = 'AUTO' | OutputLanguage;
export type TranslationMode = 'NATURAL' | 'LITERAL' | 'PROFESSIONAL' | 'REWRITE' | 'GRAMMAR';
export type GenerationPart = 'COMPLETE' | 'SUBJECT' | 'BODY';

export interface WritingPreferences {
  presetId: string;
  inputLanguage: InputLanguage;
  outputLanguage: OutputLanguage;
  translationMode: TranslationMode;
  formality: 'NEUTRAL' | 'FORMAL' | 'INFORMAL';
  tone: 'PROFESSIONAL' | 'FRIENDLY' | 'DIPLOMATIC' | 'CONCISE' | 'CUSTOM';
  length: 'SHORT' | 'MEDIUM' | 'LONG';
  audience: string;
  purpose: string;
  customInstructions: string;
  selectedRuleIds: string[];
}

export interface EmailComposition {
  accountId: string;
  recipientName: string;
  to: string;
  cc: string;
  bcc: string;
  subject: string;
  body: string;
  generationPart: GenerationPart;
}

export interface EmailAccount {
  id: string;
  name: string;
  email: string;
  provider: string;
  connected: boolean;
  permissions: string[];
  mock: boolean;
}

export interface WritingPreset {
  id: string;
  name: string;
  description: string;
  workflow: WritingWorkflow;
  enabled: boolean;
  ruleIds: string[];
  markdown: string;
  preferences?: WritingPreferences;
}

export interface WritingOptions {
  workflows: { id: WritingWorkflow; label: string; supported: boolean }[];
  presets: WritingPreset[];
  accounts: EmailAccount[];
  capabilities: { generation: 'MOCK'; sending: boolean; recipientDiscovery?: 'MOCK'; attachments?: boolean };
}

export interface WritingRequest extends ModelSelection {
  workflow: WritingWorkflow;
  input: string;
  modelId: string;
  preferences: WritingPreferences;
  email: EmailComposition;
}

export interface DetectedLanguage {
  language: OutputLanguage | 'UNKNOWN' | 'MIXED';
  mock: boolean;
  method: string;
}

export interface WritingResult extends ResolvedModelMetadata {
  id: string;
  title: string;
  content: string;
  subject: string;
  workflow: WritingWorkflow;
  detectedLanguage: DetectedLanguage;
  outputLanguage: OutputLanguage;
  translationMode: TranslationMode;
  mock: boolean;
  appliedRuleIds: string[];
  createdAt: string;
  modelId?: string;
}

export interface WritingDraft extends WritingRequest {
  id: string;
  title: string;
  content: string;
  subject: string;
  status: 'DRAFT';
  revision: number;
  updatedAt: string;
}

export interface InstructionPreview { systemPrompt: string; appliedRuleIds: string[]; }

export interface WritingDocument extends WritingDraft {
  lastExecution?: ModelExecution | null;
  dirty: boolean;
  editVersion: number;
  manualContent: boolean;
  manualSubject: boolean;
  detectedLanguage: DetectedLanguage | null;
  appliedRuleIds: string[];
}

export const WORKFLOWS: WritingOptions['workflows'] = [
  { id: 'GENERAL', label: 'General writing', supported: true },
  { id: 'EMAIL', label: 'Email', supported: true },
  { id: 'TRANSLATION', label: 'Translation', supported: true },
  { id: 'CUSTOM_DOCUMENT', label: 'Custom document', supported: true },
  { id: 'CV', label: 'CV / résumé', supported: false },
  { id: 'COVER_LETTER', label: 'Cover letter', supported: false },
  { id: 'JOB_APPLICATION', label: 'Job application', supported: false }
];

export const DEFAULT_PREFERENCES: WritingPreferences = {
  presetId: '', inputLanguage: 'AUTO', outputLanguage: 'EN', translationMode: 'NATURAL',
  formality: 'NEUTRAL', tone: 'PROFESSIONAL', length: 'MEDIUM', audience: '', purpose: '',
  customInstructions: '', selectedRuleIds: []
};

export function newWritingDocument(workflow: WritingWorkflow, modelId = '', accountId = ''): WritingDocument {
  return {
    id: '', title: '', workflow, input: '', content: '', subject: '', modelId, conversationId: crypto.randomUUID(),
    preferences: { ...DEFAULT_PREFERENCES, selectedRuleIds: [] },
    email: { accountId, recipientName: '', to: '', cc: '', bcc: '', subject: '', body: '', generationPart: 'COMPLETE' },
    status: 'DRAFT', revision: 0, updatedAt: '', dirty: false, editVersion: 0,
    manualContent: false, manualSubject: false, detectedLanguage: null, appliedRuleIds: []
  };
}
