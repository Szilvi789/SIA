import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { WorkspaceService } from '../core/workspace.service';
import { IconComponent } from '../shared/icon.component';
import { DocumentExportService } from './export/document-export.service';
import { DocumentExportFormat } from './export/export.models';
import { DEFAULT_PREFERENCES, GenerationPart, WritingDraft, WritingPreset, WritingWorkflow } from './writing.models';
import { WritingStateService } from './writing-state.service';

@Component({
  standalone: true,
  imports: [FormsModule, MatButtonModule, IconComponent],
  templateUrl: './writing.component.html',
  styleUrl: './writing.component.scss'
})
export class WritingComponent {
  readonly state = inject(WritingStateService);
  readonly workspace = inject(WorkspaceService);
  private readonly exports = inject(DocumentExportService);
  readonly section = signal<'workspace' | 'drafts' | 'presets' | 'preview'>('workspace');
  readonly exporting = signal(false);
  readonly exportError = signal('');
  readonly localStatus = signal('');
  readonly sender = computed(() => this.state.options().accounts.find(account => account.id === this.state.document().email.accountId));
  readonly workflowLabel = computed(() => this.state.options().workflows.find(item => item.id === this.state.workflow())?.label ?? 'Writing');
  readonly languageNames: Record<string, string> = { EN: 'English', DE: 'German', HU: 'Hungarian', UNKNOWN: 'Unknown', MIXED: 'Mixed languages' };
  exportFormat: DocumentExportFormat = 'PDF';
  emailReviewed = false;
  presetEditor: WritingPreset = this.emptyPreset();
  presetExisting = false;
  presetDirty = false;

  constructor() { this.state.load(); }

  selectWorkflow(workflow: WritingWorkflow): void {
    this.state.selectWorkflow(workflow);
    this.emailReviewed = false;
    this.localStatus.set('');
  }

  generate(part: GenerationPart = 'COMPLETE'): void {
    const confirmationNeeded = this.state.requiresReplacementConfirmation(part);
    if (confirmationNeeded && !window.confirm('Replace the manually edited ' + (part === 'SUBJECT' ? 'subject' : part === 'BODY' ? 'body' : 'document') + '? Save a draft first to keep a copy.')) return;
    this.emailReviewed = false;
    this.state.generate(part, confirmationNeeded);
  }

  loadDraft(draft: WritingDraft): void {
    const confirmationNeeded = this.state.wouldDiscardDraft(draft);
    if (confirmationNeeded && !window.confirm('Replace the unsaved document in this workflow with this saved draft?')) return;
    if (this.state.loadDraft(draft, confirmationNeeded)) {
      this.section.set('workspace');
      this.emailReviewed = false;
    }
  }

  applyPreset(id: string): void {
    const preset = this.state.availablePresets().find(item => item.id === id);
    if (preset?.preferences && this.state.document().dirty
      && !window.confirm('Apply this preset’s writing settings? This replaces the current preferences and keeps your input and document text.')) return;
    this.state.applyPreset(id);
  }

  async copy(): Promise<void> {
    this.localStatus.set('');
    try {
      const doc = this.state.document();
      await navigator.clipboard.writeText(doc.workflow === 'EMAIL' && doc.subject ? `${doc.subject}\n\n${doc.content}` : doc.content);
      this.localStatus.set('Latest edited text copied.');
    } catch {
      this.localStatus.set('Clipboard unavailable. Select the document text and copy it manually.');
    }
  }

  async exportDocument(): Promise<void> {
    if (this.exporting() || !this.state.document().content.trim()) return;
    this.exporting.set(true);
    this.exportError.set('');
    this.localStatus.set('');
    const doc = this.state.document();
    try {
      const artifacts = await this.exports.export(this.exportFormat, {
        title: doc.title || this.workflowLabel(), content: doc.content,
        ...(doc.workflow === 'EMAIL' ? { subject: doc.subject } : {})
      });
      for (const artifact of artifacts) {
        const url = URL.createObjectURL(artifact.blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = artifact.filename;
        document.body.appendChild(link);
        link.click();
        link.remove();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
      }
      this.localStatus.set(artifacts.length > 1 ? `${artifacts.length} document pages exported.` : 'Latest edited document exported.');
    } catch (error: unknown) {
      this.exportError.set(error instanceof Error ? error.message : 'Export could not be completed. Please try another format.');
    } finally { this.exporting.set(false); }
  }

  editPreset(preset?: WritingPreset): void {
    if (this.presetDirty && !window.confirm('Discard unsaved changes to this Markdown preset?')) return;
    this.presetEditor = preset ? structuredClone(preset) : this.emptyPreset();
    this.presetEditor.preferences ??= { ...DEFAULT_PREFERENCES, selectedRuleIds: [] };
    this.presetExisting = !!preset;
    this.presetDirty = false;
  }

  setRuleIds(value: string): void {
    this.presetEditor.ruleIds = value.split(',').map(id => id.trim()).filter(Boolean);
    this.presetDirty = true;
  }

  validPreset(): boolean {
    return /^[a-z0-9][a-z0-9-]{0,59}$/.test(this.presetEditor.id)
      && !!this.presetEditor.name.trim() && this.presetEditor.name.length <= 80
      && this.presetEditor.description.length <= 500
      && !!this.presetEditor.markdown.trim() && this.presetEditor.markdown.length <= 16000;
  }

  savePreset(): void {
    if (!this.validPreset()) return;
    this.state.savePreset(structuredClone(this.presetEditor), this.presetExisting, saved => {
      this.presetEditor = structuredClone(saved);
      this.presetExisting = true;
      this.presetDirty = false;
    });
  }

  private emptyPreset(): WritingPreset {
    return {
      id: '', name: '', description: '', workflow: this.state?.workflow() ?? 'GENERAL', enabled: true, ruleIds: [], markdown: '',
      preferences: { ...DEFAULT_PREFERENCES, selectedRuleIds: [] }
    };
  }
}
