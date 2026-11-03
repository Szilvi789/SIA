import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { WorkspaceService } from '../core/workspace.service';
import { generatedResult, savedDraft, writingOptions } from './writing-test-data';
import { WritingStateService } from './writing-state.service';

describe('WritingStateService', () => {
  let state: WritingStateService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [
      provideHttpClient(), provideHttpClientTesting(),
      { provide: WorkspaceService, useValue: { selectedModelId: signal('mock-precise') } }
    ] });
    http = TestBed.inject(HttpTestingController);
    state = TestBed.inject(WritingStateService);
    state.load();
    http.expectOne('/api/writing/options').flush(writingOptions());
    http.expectOne('/api/writing/drafts').flush([]);
  });

  afterEach(() => http.verify());

  it('keeps input and output language independent and chooses the workspace model', () => {
    state.updatePreferences({ inputLanguage: 'HU', outputLanguage: 'DE' });
    state.updatePreferences({ inputLanguage: 'EN' });
    expect(state.document().preferences.outputLanguage).toBe('DE');
    expect(state.document().modelId).toBe('mock-precise');
    expect(state.document().email.accountId).toBe('mock-personal');
  });

  it('retains unsaved documents and settings separately across workflow changes', () => {
    state.updateField('input', 'A general document');
    state.updateContent('General manual edits');
    state.updatePreferences({ outputLanguage: 'DE' });
    state.selectWorkflow('EMAIL');
    state.updateField('input', 'An email');
    state.updateSubject('Email subject');
    state.selectWorkflow('GENERAL');
    expect(state.document().content).toBe('General manual edits');
    expect(state.document().preferences.outputLanguage).toBe('DE');
    state.selectWorkflow('EMAIL');
    expect(state.document().input).toBe('An email');
    expect(state.document().subject).toBe('Email subject');
    state.selectWorkflow('CV');
    expect(state.workflow()).toBe('EMAIL');
  });

  it('applies structured preset preferences and deduplicates rule assignments', () => {
    state.updateField('input', 'Keep this input');
    state.updateContent('Keep this edited output');
    state.applyPreset('professional');
    expect(state.document().preferences).toMatchObject({ presetId: 'professional', outputLanguage: 'DE', tone: 'DIPLOMATIC', selectedRuleIds: ['writing-professional'] });
    expect(state.document().content).toBe('Keep this edited output');
    expect(state.document().input).toBe('Keep this input');
    state.selectWorkflow('EMAIL');
    expect(state.availablePresets().map(preset => preset.id)).toContain('professional');
  });

  it('blocks manual overwrite unless explicitly confirmed', () => {
    state.updateField('input', 'Rewrite this');
    state.updateContent('My manual text');
    state.generate();
    http.expectNone('/api/writing/generate');
    state.generate('COMPLETE', true);
    const request = http.expectOne('/api/writing/generate');
    expect(request.request.body.modelId).toBe('mock-precise');
    request.flush(generatedResult());
    expect(state.document().content).toBe('A mock document.');
    expect(state.document().manualContent).toBe(false);
    expect(state.document().detectedLanguage?.mock).toBe(true);
  });

  it('freezes a generating workflow, prevents duplicate generation, and returns to its original document', () => {
    state.updateField('input', 'Original task');
    state.generate();
    state.generate();
    state.updateField('input', 'Too late');
    state.updateContent('Too late');
    const request = http.expectOne('/api/writing/generate');
    expect(request.request.body.input).toBe('Original task');
    state.selectWorkflow('EMAIL');
    state.updateContent('Different workflow content');
    request.flush(generatedResult());
    expect(state.document().content).toBe('Different workflow content');
    expect(state.document().workflow).toBe('EMAIL');
    state.selectWorkflow('GENERAL');
    expect(state.document().input).toBe('Original task');
    expect(state.document().content).toBe('A mock document.');
    expect(state.generating()).toBe(false);
  });

  it('preserves the email body when generating only a subject', () => {
    state.selectWorkflow('EMAIL');
    state.updateField('input', 'Suggest a subject');
    state.updateContent('My manually edited email body');
    expect(state.requiresReplacementConfirmation('SUBJECT')).toBe(false);
    state.generate('SUBJECT');
    const request = http.expectOne('/api/writing/generate');
    expect(request.request.body.email).toMatchObject({ generationPart: 'SUBJECT', body: 'My manually edited email body' });
    request.flush({ ...generatedResult(), workflow: 'EMAIL' });
    expect(state.document().content).toBe('My manually edited email body');
    expect(state.document().email.body).toBe('My manually edited email body');
    expect(state.document().manualContent).toBe(true);
    expect(state.document().subject).toBe('A mock subject');
  });

  it('preserves the email subject when generating only a body', () => {
    state.selectWorkflow('EMAIL');
    state.updateField('input', 'Write an email');
    state.updateSubject('Keep my subject');
    expect(state.requiresReplacementConfirmation('BODY')).toBe(false);
    state.generate('BODY');
    http.expectOne('/api/writing/generate').flush({ ...generatedResult(), workflow: 'EMAIL' });
    expect(state.document().subject).toBe('Keep my subject');
    expect(state.document().email.subject).toBe('Keep my subject');
    expect(state.document().manualSubject).toBe(true);
    expect(state.document().content).toBe('A mock document.');
  });

  it('preserves input and output on generation failure and permits a retry', () => {
    state.updateField('input', 'Retry this');
    state.updateContent('Keep my output on failure');
    state.generate('COMPLETE', true);
    http.expectOne('/api/writing/generate').flush({ detail: 'Model unavailable.' }, { status: 503, statusText: 'Unavailable' });
    expect(state.error()).toBe('Model unavailable.');
    expect(state.document().content).toBe('Keep my output on failure');
    expect(state.document().input).toBe('Retry this');
    expect(state.generating()).toBe(false);
    state.generate('COMPLETE', true);
    http.expectOne('/api/writing/generate').flush(generatedResult());
  });

  it('saves the latest edited snapshot and preserves newer edits made during the request', () => {
    state.updateField('input', 'Initial task');
    state.updateContent('Version being saved');
    state.saveDraft();
    state.saveDraft();
    const create = http.expectOne('/api/writing/drafts');
    expect(create.request.method).toBe('POST');
    expect(create.request.body.content).toBe('Version being saved');
    expect(create.request.body.modelId).toBe('mock-precise');
    expect(create.request.body.dirty).toBeUndefined();
    state.updateContent('Newer manual version');
    create.flush({ ...create.request.body, id: 'draft-new', revision: 1, updatedAt: '2026-11-03T12:00:00Z' });
    expect(state.document()).toMatchObject({ id: 'draft-new', content: 'Newer manual version', revision: 1, dirty: true });
    state.saveDraft();
    const update = http.expectOne('/api/writing/drafts/draft-new');
    expect(update.request.method).toBe('PUT');
    expect(update.request.body).toMatchObject({ content: 'Newer manual version', revision: 1 });
    update.flush({ ...update.request.body, revision: 2, updatedAt: '2026-11-03T12:01:00Z' });
    expect(state.document().dirty).toBe(false);
    expect(state.drafts()).toHaveLength(1);
    expect(state.document().revision).toBe(2);
  });

  it('preserves all local changes when a draft revision conflicts', () => {
    state.loadDraft(savedDraft());
    state.updateContent('Uncommitted newer version');
    state.saveDraft();
    http.expectOne('/api/writing/drafts/draft-1').flush({ detail: 'Draft changed. Reload before saving.' }, { status: 409, statusText: 'Conflict' });
    expect(state.document().content).toBe('Uncommitted newer version');
    expect(state.document().dirty).toBe(true);
    expect(state.document().revision).toBe(1);
    expect(state.saving()).toBe(false);
    expect(state.error()).toContain('Reload');
  });

  it('requires permission to replace an unsaved document and restores saved task configuration', () => {
    state.updateContent('Unsaved content');
    expect(state.loadDraft(savedDraft())).toBe(false);
    expect(state.document().content).toBe('Unsaved content');
    expect(state.loadDraft(savedDraft(), true)).toBe(true);
    expect(state.document()).toMatchObject({ title: 'Saved title', input: 'My saved instructions', content: 'Saved manual text', dirty: false, manualContent: true });
    expect(state.document().preferences.outputLanguage).toBe('HU');
    expect(state.requiresReplacementConfirmation('COMPLETE')).toBe(true);
  });

  it('previews composed instructions for the exact current preferences', () => {
    state.updateField('input', 'My task');
    state.updatePreferences({ inputLanguage: 'HU', outputLanguage: 'DE', customInstructions: 'No greeting.' });
    state.previewInstructions();
    const request = http.expectOne('/api/writing/preview-instructions');
    expect(request.request.body.preferences).toMatchObject({ inputLanguage: 'HU', outputLanguage: 'DE', customInstructions: 'No greeting.' });
    request.flush({ systemPrompt: 'Core safety\nGlobal instructions\nPreset rules', appliedRuleIds: ['writing-professional'] });
    expect(state.instructionPreview()?.systemPrompt).toContain('Core safety');
    expect(state.previewing()).toBe(false);
  });

  it('updates preset options only after a successful save and reports failures without a success callback', () => {
    const onSaved = vi.fn();
    const preset = writingOptions().presets[0];
    state.savePreset({ ...preset, name: 'Updated preset' }, true, onSaved);
    http.expectOne('/api/writing/presets/professional').flush({ detail: 'Invalid rule.' }, { status: 400, statusText: 'Bad Request' });
    expect(onSaved).not.toHaveBeenCalled();
    expect(state.options().presets[0].name).toBe('Professional');
    expect(state.presetSaving()).toBe(false);
    state.savePreset({ ...preset, name: 'Updated preset' }, true, onSaved);
    http.expectOne('/api/writing/presets/professional').flush({ ...preset, name: 'Updated preset' });
    expect(state.options().presets[0].name).toBe('Updated preset');
    expect(onSaved).toHaveBeenCalledOnce();
  });
});
