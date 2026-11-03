import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { WorkspaceService } from '../core/workspace.service';
import { DocumentExportService } from './export/document-export.service';
import { generatedResult, savedDraft, writingOptions } from './writing-test-data';
import { WritingComponent } from './writing.component';
import { WritingStateService } from './writing-state.service';

describe('WritingComponent', () => {
  let fixture: ComponentFixture<WritingComponent>;
  let component: WritingComponent;
  let state: WritingStateService;
  let http: HttpTestingController;
  const exporter = { export: vi.fn() };

  beforeEach(async () => {
    exporter.export.mockReset();
    await TestBed.configureTestingModule({
      imports: [WritingComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(),
        { provide: WorkspaceService, useValue: { selectedModelId: signal('mock-balanced'), models: signal([{ id: 'mock-balanced', name: 'Mock Balanced' }]) } },
        { provide: DocumentExportService, useValue: exporter }
      ]
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    state = TestBed.inject(WritingStateService);
    fixture = TestBed.createComponent(WritingComponent);
    component = fixture.componentInstance;
    http.expectOne('/api/writing/options').flush(writingOptions());
    http.expectOne('/api/writing/drafts').flush([]);
    fixture.detectChanges();
  });

  afterEach(() => { http.verify(); vi.restoreAllMocks(); vi.useRealTimers(); vi.unstubAllGlobals(); });

  it('renders one coherent workspace with honest mock labels and disabled Phase 2 modes', () => {
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Mock workspace');
    expect(text).toContain('Output language');
    expect(text).toContain('do not provide accurate AI writing or translation');
    const modes = [...fixture.nativeElement.querySelectorAll('.workflow-strip button')] as HTMLButtonElement[];
    expect(modes).toHaveLength(7);
    expect(modes.filter(button => button.disabled)).toHaveLength(3);
    expect(modes.find(button => button.textContent?.includes('CV'))?.textContent).toContain('Phase 2');
  });

  it('asks explicitly before replacing manual output and respects cancellation', () => {
    state.updateField('input', 'Make this clearer');
    state.updateContent('My manual draft');
    const confirmation = vi.spyOn(window, 'confirm').mockReturnValue(false);
    component.generate();
    expect(confirmation).toHaveBeenCalledOnce();
    http.expectNone('/api/writing/generate');
    expect(state.document().content).toBe('My manual draft');
    confirmation.mockReturnValue(true);
    component.generate();
    http.expectOne('/api/writing/generate').flush(generatedResult());
    expect(state.document().content).toBe('A mock document.');
  });

  it('records document editor changes as manual edits and protects them from regeneration', async () => {
    const output = fixture.nativeElement.querySelector('#writing-output') as HTMLTextAreaElement;
    output.value = 'Typed directly into the editor';
    output.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    await fixture.whenStable();
    expect(state.document().content).toBe('Typed directly into the editor');
    expect(state.document().dirty).toBe(true);
    expect(state.requiresReplacementConfirmation('COMPLETE')).toBe(true);
  });

  it('confirms before replacing an unsaved workflow with a saved draft', () => {
    state.updateContent('Unsaved document');
    const confirmation = vi.spyOn(window, 'confirm').mockReturnValue(false);
    component.loadDraft(savedDraft());
    expect(state.document().content).toBe('Unsaved document');
    confirmation.mockReturnValue(true);
    component.loadDraft(savedDraft());
    expect(state.document().content).toBe('Saved manual text');
    expect(component.section()).toBe('workspace');
  });

  it('keeps unsaved work when switching workflows without requiring a destructive confirmation', () => {
    const confirmation = vi.spyOn(window, 'confirm');
    state.updateContent('General document');
    component.selectWorkflow('EMAIL');
    state.updateContent('Email body');
    component.selectWorkflow('GENERAL');
    expect(state.document().content).toBe('General document');
    expect(confirmation).not.toHaveBeenCalled();
  });

  it('asks before applying preset preferences over unsaved writing settings', () => {
    state.updatePreferences({ outputLanguage: 'HU', tone: 'FRIENDLY' });
    const confirmation = vi.spyOn(window, 'confirm').mockReturnValue(false);
    component.applyPreset('professional');
    expect(state.document().preferences.outputLanguage).toBe('HU');
    confirmation.mockReturnValue(true);
    component.applyPreset('professional');
    expect(state.document().preferences).toMatchObject({ outputLanguage: 'DE', tone: 'DIPLOMATIC', presetId: 'professional' });
  });

  it('turns a successful new preset into an existing editable preset and uses PUT on the second save', () => {
    component.presetEditor = { id: 'my-preset', name: 'My preset', description: '', workflow: 'GENERAL', enabled: true, ruleIds: [], markdown: '# My style\nNo greeting.' };
    component.presetDirty = true;
    component.savePreset();
    const create = http.expectOne('/api/writing/presets');
    expect(create.request.method).toBe('POST');
    create.flush(component.presetEditor);
    expect(component.presetExisting).toBe(true);
    expect(component.presetDirty).toBe(false);
    component.presetEditor.markdown += '\nNo closing.';
    component.presetDirty = true;
    component.savePreset();
    const update = http.expectOne('/api/writing/presets/my-preset');
    expect(update.request.method).toBe('PUT');
    update.flush(component.presetEditor);
    expect(component.presetDirty).toBe(false);
  });

  it('retains unsaved preset edits on save errors and validates the backend slug boundary', () => {
    component.presetEditor = { id: 'my-preset', name: 'My preset', description: '', workflow: 'GENERAL', enabled: true, ruleIds: [], markdown: '# My style' };
    component.presetDirty = true;
    component.savePreset();
    http.expectOne('/api/writing/presets').flush({ detail: 'Preset rejected.' }, { status: 400, statusText: 'Bad Request' });
    expect(component.presetDirty).toBe(true);
    expect(component.presetExisting).toBe(false);
    expect(component.presetEditor.markdown).toBe('# My style');
    component.presetEditor.id = 'a'.repeat(60);
    expect(component.validPreset()).toBe(true);
    component.presetEditor.id += 'a';
    expect(component.validPreset()).toBe(false);
  });

  it('protects unsaved Markdown editor changes when selecting another preset', () => {
    component.presetEditor.markdown = 'Unsaved Markdown';
    component.presetDirty = true;
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    component.editPreset(writingOptions().presets[0]);
    expect(component.presetEditor.markdown).toBe('Unsaved Markdown');
  });

  it('shows exact email sender and current manual subject/body while disabling sending and attachments', async () => {
    component.selectWorkflow('EMAIL');
    state.updateEmail({ to: 'recipient@example.com', cc: 'copy@example.com', bcc: 'private@example.com', recipientName: 'Ada' });
    state.updateSubject('Edited subject');
    state.updateContent('Latest manually edited body');
    component.section.set('preview');
    fixture.detectChanges();
    await fixture.whenStable();
    const preview = fixture.nativeElement.querySelector('.email-preview') as HTMLElement;
    expect(preview.textContent).toContain('personal@example.invalid');
    expect(preview.textContent).toContain('recipient@example.com');
    expect(preview.textContent).toContain('copy@example.com');
    expect(preview.textContent).toContain('private@example.com');
    expect(preview.textContent).toContain('Edited subject');
    expect(preview.textContent).toContain('Latest manually edited body');
    expect(preview.textContent).toContain('live sending is unavailable');
    const send = Array.from(preview.querySelectorAll('button')).find(button => button.textContent?.includes('Send email'));
    expect(send?.disabled).toBe(true);
    const review = preview.querySelector('input[type="checkbox"]') as HTMLInputElement;
    review.checked = true;
    review.dispatchEvent(new Event('change'));
    await fixture.whenStable();
    fixture.detectChanges();
    expect(component.emailReviewed).toBe(true);
    expect(send?.disabled).toBe(true);
  });

  it('passes the latest manual document to export without generating text and reports converter errors', async () => {
    state.selectWorkflow('EMAIL');
    state.updateField('title', 'My edited title');
    state.updateSubject('My edited subject');
    state.updateContent('My final manual content');
    exporter.export.mockRejectedValue(new Error('PDF conversion unavailable.'));
    await component.exportDocument();
    expect(exporter.export).toHaveBeenCalledExactlyOnceWith('PDF', {
      title: 'My edited title', subject: 'My edited subject', content: 'My final manual content'
    });
    http.expectNone('/api/writing/generate');
    expect(component.exportError()).toBe('PDF conversion unavailable.');
    expect(component.exporting()).toBe(false);
    expect(state.document().content).toBe('My final manual content');
  });

  it('downloads all returned export pages and prevents duplicate export requests while pending', async () => {
    vi.useFakeTimers();
    state.updateContent('Current manual document');
    exporter.export.mockResolvedValue([
      { filename: 'document-page-1.png', blob: new Blob(['page1']) },
      { filename: 'document-page-2.png', blob: new Blob(['page2']) }
    ]);
    const createUrl = vi.fn().mockReturnValue('blob:test');
    vi.stubGlobal('URL', class extends URL { static override createObjectURL = createUrl; static override revokeObjectURL = vi.fn(); });
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);
    component.exportFormat = 'PNG';
    const exportPending = component.exportDocument();
    await component.exportDocument();
    await exportPending;
    expect(exporter.export).toHaveBeenCalledOnce();
    expect(createUrl).toHaveBeenCalledTimes(2);
    expect(click).toHaveBeenCalledTimes(2);
    expect(component.localStatus()).toBe('2 document pages exported.');
    vi.runAllTimers();
  });
});
