import { DEFAULT_PREFERENCES, newWritingDocument, WORKFLOWS, WritingDraft, WritingOptions, WritingResult } from './writing.models';

export function writingOptions(): WritingOptions {
  return {
    workflows: WORKFLOWS,
    presets: [{
      id: 'professional', name: 'Professional', description: 'Clear professional writing.', workflow: 'GENERAL',
      enabled: true, ruleIds: ['writing-professional'], markdown: '# Professional\nKeep it clear.',
      preferences: { ...DEFAULT_PREFERENCES, outputLanguage: 'DE', tone: 'DIPLOMATIC', selectedRuleIds: ['writing-professional'] }
    }],
    accounts: [{ id: 'mock-personal', name: 'Personal', email: 'personal@example.invalid', provider: 'MOCK', connected: true, permissions: ['MANAGE_DRAFTS'], mock: true }],
    capabilities: { generation: 'MOCK', sending: false, attachments: false }
  };
}

export function generatedResult(): WritingResult {
  return {
    id: 'generation-1', title: 'A useful draft', content: 'A mock document.', subject: 'A mock subject', workflow: 'GENERAL',
    detectedLanguage: { language: 'HU', mock: true, method: 'demo-heuristic' },
    outputLanguage: 'DE', translationMode: 'NATURAL', mock: true, appliedRuleIds: ['writing-professional'], createdAt: '2026-11-03T11:00:00Z'
  };
}

export function savedDraft(): WritingDraft {
  return {
    ...newWritingDocument('GENERAL'), id: 'draft-1', title: 'Saved title', input: 'My saved instructions', content: 'Saved manual text',
    preferences: { ...DEFAULT_PREFERENCES, outputLanguage: 'HU', selectedRuleIds: ['writing-professional'] },
    revision: 1, updatedAt: '2026-11-03T12:00:00Z'
  };
}
