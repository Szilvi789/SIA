import { computed, inject, Injectable, signal } from '@angular/core';
import { forkJoin } from 'rxjs';
import { errorMessage } from '../core/api.service';
import { WorkspaceService } from '../core/workspace.service';
import { ModelSelection } from '../core/models';
import { WritingApiService } from './writing-api.service';
import {
  EmailComposition, GenerationPart, InstructionPreview, newWritingDocument, WORKFLOWS,
  WritingDocument, WritingDraft, WritingOptions, WritingPreferences, WritingPreset, WritingRequest, WritingWorkflow
} from './writing.models';

/** Session state survives navigation. Each workflow keeps its own unsaved document. */
@Injectable({ providedIn: 'root' })
export class WritingStateService {
  private readonly api = inject(WritingApiService);
  private readonly workspace = inject(WorkspaceService);
  private readonly documents = signal<Partial<Record<WritingWorkflow, WritingDocument>>>({});
  private requestSequence = 0;
  private loaded = false;
  readonly workflow = signal<WritingWorkflow>('GENERAL');
  readonly options = signal<WritingOptions>({ workflows: WORKFLOWS, presets: [], accounts: [], capabilities: { generation: 'MOCK', sending: false } });
  readonly drafts = signal<WritingDraft[]>([]);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly status = signal('');
  readonly pending = signal<{ id: number; workflow: WritingWorkflow } | null>(null);
  readonly saving = signal(false);
  readonly presetSaving = signal(false);
  readonly previewing = signal(false);
  readonly instructionPreview = signal<InstructionPreview | null>(null);
  readonly document = computed(() => this.documents()[this.workflow()] ?? newWritingDocument(this.workflow(), '', this.options().accounts[0]?.id));
  readonly modelSelection = computed<ModelSelection>(() => ({ modelId: this.document().modelId || undefined, overrideModelId: this.document().overrideModelId, overrideTier: this.document().overrideTier }));
  readonly generating = computed(() => this.pending() !== null);
  readonly locked = computed(() => this.pending()?.workflow === this.workflow());
  readonly availablePresets = computed(() => this.options().presets.filter(preset => preset.enabled && (preset.workflow === 'GENERAL' || preset.workflow === this.workflow())));

  load(force = false): void {
    if ((this.loaded && !force) || this.loading()) return;
    this.loading.set(true);
    this.error.set('');
    forkJoin({ options: this.api.options(), drafts: this.api.drafts() }).subscribe({
      next: ({ options, drafts }) => {
        this.options.set(options);
        this.drafts.set(drafts);
        const accountId = options.accounts[0]?.id ?? '';
        this.documents.update(documents => Object.fromEntries(Object.entries(documents).map(([key, value]) => [key,
          value && !value.email.accountId ? { ...value, email: { ...value.email, accountId } } : value
        ])));
        this.loaded = true;
        this.loading.set(false);
      },
      error: (error: unknown) => { this.loading.set(false); this.error.set(errorMessage(error)); }
    });
  }

  selectWorkflow(workflow: WritingWorkflow): void {
    if (!this.options().workflows.some(item => item.id === workflow && item.supported)) return;
    if (!this.documents()[workflow]) {
      const doc = newWritingDocument(workflow, '', this.options().accounts[0]?.id);
      this.documents.update(documents => ({ ...documents, [workflow]: doc }));
    }
    this.workflow.set(workflow);
    this.instructionPreview.set(null);
    this.error.set('');
    this.status.set('');
  }

  updateField(field: 'input' | 'title' | 'modelId', value: string): void {
    if (!this.locked()) this.edit({ [field]: value });
  }

  selectModel(selection: ModelSelection): void {
    if (!this.locked()) this.edit({ modelId: '', overrideModelId: selection.overrideModelId, overrideTier: selection.overrideTier });
  }

  updatePreferences(patch: Partial<WritingPreferences>): void {
    if (!this.locked()) { this.edit({ preferences: { ...this.document().preferences, ...patch } }); this.instructionPreview.set(null); }
  }

  updateEmail(patch: Partial<EmailComposition>): void {
    if (this.locked()) return;
    const changes: Partial<WritingDocument> = { email: { ...this.document().email, ...patch } };
    if (patch.subject !== undefined) { changes.subject = patch.subject; changes.manualSubject = true; }
    if (patch.body !== undefined) { changes.content = patch.body; changes.manualContent = true; }
    this.edit(changes);
  }

  updateContent(content: string): void {
    if (this.locked()) return;
    this.edit({ content, manualContent: true, email: { ...this.document().email, body: content } });
  }

  updateSubject(subject: string): void {
    if (this.locked()) return;
    this.edit({ subject, manualSubject: true, email: { ...this.document().email, subject } });
  }

  applyPreset(id: string): void {
    if (this.locked()) return;
    const preset = this.availablePresets().find(item => item.id === id);
    this.updatePreferences({
      ...preset?.preferences,
      presetId: preset?.id ?? '',
      selectedRuleIds: [...new Set([...(preset?.ruleIds ?? []), ...(preset?.preferences?.selectedRuleIds ?? [])])]
    });
  }

  requiresReplacementConfirmation(part: GenerationPart): boolean {
    const doc = this.document();
    return (part !== 'SUBJECT' && doc.manualContent) || (doc.workflow === 'EMAIL' && part !== 'BODY' && doc.manualSubject);
  }

  generate(part: GenerationPart = 'COMPLETE', replacementConfirmed = false): void {
    if (this.generating() || !this.document().input.trim() || (this.requiresReplacementConfirmation(part) && !replacementConfirmed)) return;
    const snapshot = structuredClone(this.document());
    const id = ++this.requestSequence;
    const request = this.request(snapshot, part);
    this.pending.set({ id, workflow: snapshot.workflow });
    this.error.set('');
    this.status.set('');
    this.api.generate(request).subscribe({
      next: result => {
        if (this.pending()?.id !== id) return;
        const current = this.documents()[snapshot.workflow] ?? snapshot;
        // A response belongs to its original workflow and must never replace later edits.
        if (current.editVersion === snapshot.editVersion) {
          const subject = snapshot.workflow === 'EMAIL' && part === 'BODY' ? current.subject : result.subject;
          const content = snapshot.workflow === 'EMAIL' && part === 'SUBJECT' ? current.content : result.content;
          this.setDocument(snapshot.workflow, {
            ...current, title: current.title || result.title, content, subject,
            email: { ...current.email, subject, body: content, generationPart: part },
            detectedLanguage: result.detectedLanguage, appliedRuleIds: result.appliedRuleIds, lastExecution: result.execution,
            manualContent: part === 'SUBJECT' ? current.manualContent : false,
            manualSubject: part === 'BODY' ? current.manualSubject : false,
            dirty: true, editVersion: current.editVersion + 1
          });
          this.status.set(`${result.mock ? 'Mock draft' : 'Draft'} generated${result.modelId ? ' with ' + result.providerId + ' / ' + result.modelId : ''}. Review and edit before using it.`);
        }
        this.pending.set(null);
      },
      error: (error: unknown) => { if (this.pending()?.id === id) { this.pending.set(null); this.error.set(errorMessage(error)); } }
    });
  }

  saveDraft(): void {
    if (this.saving() || this.locked()) return;
    const snapshot = structuredClone(this.document());
    if (!snapshot.input.trim() && !snapshot.content.trim() && !snapshot.subject.trim()) return;
    this.saving.set(true);
    this.error.set('');
    this.api.saveDraft(this.toDraft(snapshot)).subscribe({
      next: saved => {
        this.drafts.update(drafts => [saved, ...drafts.filter(draft => draft.id !== saved.id)]);
        const current = this.documents()[snapshot.workflow] ?? snapshot;
        if (current.id === snapshot.id) {
          // Preserve changes made while saving, but keep the server revision for the next update.
          this.setDocument(snapshot.workflow, {
            ...current, id: saved.id, revision: saved.revision, updatedAt: saved.updatedAt,
            dirty: current.editVersion !== snapshot.editVersion
          });
        }
        this.saving.set(false);
        this.status.set('Draft saved for this backend session.');
      },
      error: (error: unknown) => { this.saving.set(false); this.error.set(errorMessage(error)); }
    });
  }

  wouldDiscardDraft(draft: WritingDraft): boolean {
    return !!this.documents()[draft.workflow]?.dirty;
  }

  loadDraft(draft: WritingDraft, discardConfirmed = false): boolean {
    if (this.pending()?.workflow === draft.workflow || this.saving() || (this.wouldDiscardDraft(draft) && !discardConfirmed)) return false;
    this.setDocument(draft.workflow, {
      ...newWritingDocument(draft.workflow), ...structuredClone(draft),
      dirty: false, editVersion: 0, manualContent: !!draft.content, manualSubject: !!draft.subject,
      detectedLanguage: null, appliedRuleIds: []
    });
    this.workflow.set(draft.workflow);
    this.error.set('');
    this.status.set('Saved draft restored.');
    this.instructionPreview.set(null);
    return true;
  }

  savePreset(preset: WritingPreset, existing: boolean, onSaved?: (saved: WritingPreset) => void): void {
    if (this.presetSaving()) return;
    this.presetSaving.set(true);
    this.error.set('');
    this.api.savePreset(preset, existing).subscribe({
      next: saved => {
        this.options.update(options => ({ ...options, presets: [...options.presets.filter(item => item.id !== saved.id), saved] }));
        this.presetSaving.set(false);
        this.status.set('Writing preset saved for this backend session.');
        this.instructionPreview.set(null);
        onSaved?.(saved);
      },
      error: (error: unknown) => { this.presetSaving.set(false); this.error.set(errorMessage(error)); }
    });
  }

  previewInstructions(): void {
    if (this.previewing()) return;
    this.previewing.set(true);
    this.error.set('');
    this.api.previewInstructions(this.request(this.document())).subscribe({
      next: preview => { this.instructionPreview.set(preview); this.previewing.set(false); },
      error: (error: unknown) => { this.previewing.set(false); this.error.set(errorMessage(error)); }
    });
  }

  private request(doc: WritingDocument, part: GenerationPart = 'COMPLETE'): WritingRequest {
    return {
      workflow: doc.workflow, input: doc.input, modelId: doc.modelId, overrideModelId: doc.overrideModelId,
      overrideTier: doc.overrideTier, conversationId: doc.conversationId, preferences: structuredClone(doc.preferences),
      email: { ...doc.email, subject: doc.subject, body: doc.content, generationPart: part }
    };
  }

  private edit(patch: Partial<WritingDocument>): void {
    const current = this.document();
    this.setDocument(current.workflow, { ...current, ...patch, dirty: true, editVersion: current.editVersion + 1 });
    this.status.set('');
  }

  private setDocument(workflow: WritingWorkflow, doc: WritingDocument): void {
    this.documents.update(documents => ({ ...documents, [workflow]: doc }));
  }

  private toDraft(doc: WritingDocument): WritingDraft {
    const agent = this.workspace.agents().find(agent => agent.id === 'writing-agent');
    const tier = doc.overrideTier || agent?.modelSettings?.defaultTier;
    const modelId = doc.modelId || doc.overrideModelId || (tier
      ? agent?.modelSettings?.tierMappings[tier] || this.workspace.modelSettings().tierMappings[tier] || ''
      : agent?.modelId || this.workspace.modelSettings().defaultModelId || '');
    return {
      ...this.request(doc, doc.email.generationPart), modelId, id: doc.id, title: doc.title.trim() || 'Untitled document',
      content: doc.content, subject: doc.subject, status: 'DRAFT', revision: doc.revision, updatedAt: doc.updatedAt
    };
  }
}
