import { Component, computed, DestroyRef, effect, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { WorkspaceService } from '../core/workspace.service';
import { ApiService, errorMessage } from '../core/api.service';
import { IconComponent } from '../shared/icon.component';
import { agentIcon } from '../shared/agent-presentation';

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, IconComponent],
  template: `
    <div class="page-title"><div><div class="eyebrow intro-label">A TEAM THAT THINKS YOUR WAY</div><h1>Your agents, your approach.</h1><p>Give each agent a purpose, a personality, and the right instructions.</p></div><span class="page-counter">{{workspace.agents().length}} PRESET AGENTS</span></div>
    <div class="agents-layout"><aside class="agent-list panel" aria-label="Select an agent"><div class="list-heading"><span>YOUR TEAM</span><span>{{workspace.agents().length}}</span></div>@for(agent of workspace.agents(); track agent.id){<button class="agent-list-item" type="button" [class.selected]="selectedId() === agent.id" [attr.aria-pressed]="selectedId() === agent.id" [disabled]="saving()" (click)="select(agent.id)"><span [class]="'agent-icon ' + agent.id"><app-icon [name]="iconFor(agent.id)" /></span><span><strong>{{agent.name}}</strong><small>Mock provider</small></span><app-icon class="select-arrow" name="arrow" /></button>}@if(workspace.loading()){<p class="empty-loading">Loading agents…</p>}<div class="list-note"><app-icon name="spark" /><p>Start with a preset.<br>Make it a little more you.</p></div></aside>
      @if(selectedAgent(); as agent){<section class="agent-editor panel"><div class="editor-heading"><div><span class="eyebrow">AGENT CONFIGURATION</span><h2>{{agent.name}}</h2></div><button mat-stroked-button class="button-icon" (click)="chatWithAgent()" [disabled]="saving()"><app-icon name="chat" /> Try agent</button></div><form [formGroup]="form" (ngSubmit)="save()"><div class="form-section"><div class="form-section-title"><span>01</span><div><h3>The essentials</h3><p>A clear name and purpose make a good first impression.</p></div></div><mat-form-field appearance="outline"><mat-label>Agent name</mat-label><input matInput formControlName="name" maxlength="80" required><mat-error>Enter a name of up to 80 characters.</mat-error></mat-form-field><mat-form-field appearance="outline"><mat-label>Description</mat-label><textarea matInput formControlName="description" rows="2" maxlength="500" required></textarea><mat-hint>A short explanation of what this agent does best.</mat-hint><mat-error>Enter a description of up to 500 characters.</mat-error></mat-form-field></div><div class="form-section"><div class="form-section-title"><span>02</span><div><h3>Give it some direction</h3><p>Tell your agent how to think, respond, and help.</p></div></div><mat-form-field appearance="outline"><mat-label>System prompt</mat-label><textarea matInput class="prompt-input" formControlName="systemPrompt" rows="6" maxlength="8000" required></textarea><mat-hint>Instructions are passed to the mock provider; real AI integration comes later.</mat-hint><mat-error>Enter instructions of up to 8,000 characters.</mat-error></mat-form-field></div><div class="form-section"><div class="form-section-title"><span>03</span><div><h3>Fine-tune the details</h3><p>A few settings to shape the way your agent works.</p></div></div><div class="form-grid"><mat-form-field appearance="outline"><mat-label>AI provider</mat-label><mat-select formControlName="providerId" required><mat-option value="mock">Mock provider</mat-option></mat-select><mat-hint>Local demonstration responses</mat-hint></mat-form-field><mat-form-field appearance="outline"><mat-label>AI model</mat-label><mat-select formControlName="modelId" required>@for(model of workspace.models(); track model.id){<mat-option [value]="model.id">{{model.name}}</mat-option>}</mat-select><mat-hint>{{modelDescription()}}</mat-hint><mat-error>Select an available model.</mat-error></mat-form-field><mat-form-field appearance="outline"><mat-label>Temperature</mat-label><input matInput type="number" min="0" max="2" step="0.1" formControlName="temperature" required><mat-hint>0 to 2 · Lower values favor consistency</mat-hint><mat-error>Use a number between 0 and 2.</mat-error></mat-form-field><mat-form-field appearance="outline"><mat-label>Maximum output tokens</mat-label><input matInput type="number" min="64" max="8192" step="1" formControlName="maxOutputTokens" required><mat-hint>64 to 8,192 · Whole numbers only</mat-hint><mat-error>Use a whole number from 64 to 8,192.</mat-error></mat-form-field></div><div class="tools-info"><app-icon name="shield" /><div><strong>Tools are unavailable in this release.</strong><p>File access and command execution are unavailable in this foundation release.</p></div><span class="tag">No enabled tools</span></div></div><div class="save-area">@if(saveError()){<div class="error-message" role="alert">{{saveError()}}</div>}<div class="save-actions"><span class="save-status" role="status">@if(saved()){<app-icon name="check" /> Changes saved for this backend session.}@else if(form.dirty){You have unsaved changes.}@else{Configuration is up to date.}</span><button mat-flat-button type="submit" class="button-icon" [disabled]="form.invalid || !form.dirty || saving() || !workspace.online()">@if(saving()){Saving…}@else{<app-icon name="check" /> Save changes}</button></div></div></form></section>}
      @else if(!workspace.loading()){<div class="panel empty-loading">No agent configuration is available. Reconnect to the backend to load your team.</div>}
    </div>
  `,
  styleUrl: './agents.component.scss'
})
export class AgentsComponent {
  readonly workspace = inject(WorkspaceService);
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly fb = inject(FormBuilder).nonNullable;
  readonly selectedId = signal('coding-agent');
  readonly selectedAgent = computed(() => this.workspace.agents().find(agent => agent.id === this.selectedId()));
  readonly saving = signal(false);
  readonly saved = signal(false);
  readonly saveError = signal('');
  readonly iconFor = agentIcon;
  private displayedAgentId = '';
  readonly form = this.fb.group({
    name: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(80)]],
    description: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(500)]],
    systemPrompt: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(8000)]],
    providerId: ['mock', Validators.required],
    modelId: ['mock-balanced', Validators.required],
    temperature: [0.7, [Validators.required, Validators.min(0), Validators.max(2)]],
    maxOutputTokens: [2048, [Validators.required, Validators.min(64), Validators.max(8192), Validators.pattern(/^\d+$/)]]
  });

  constructor() {
    effect(() => {
      const agents = this.workspace.agents();
      if (!agents.some(agent => agent.id === this.selectedId()) && agents.length > 0) this.selectedId.set(agents[0].id);
      const agent = this.selectedAgent();
      if (agent && (agent.id !== this.displayedAgentId || !this.form.dirty)) {
        this.form.reset({ name: agent.name, description: agent.description, systemPrompt: agent.systemPrompt, providerId: agent.providerId, modelId: agent.modelId, temperature: agent.temperature, maxOutputTokens: agent.maxOutputTokens }, { emitEvent: false });
        this.displayedAgentId = agent.id;
      }
    });
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.saved.set(false));
  }

  modelDescription(): string { return this.workspace.models().find(model => model.id === this.form.controls.modelId.value)?.description ?? ''; }
  select(id: string): void { if (!this.saving()) { this.selectedId.set(id); this.saved.set(false); this.saveError.set(''); } }
  chatWithAgent(): void {
    this.workspace.selectAgent(this.selectedId());
    void this.router.navigate([this.selectedId() === 'writing-agent' ? '/writing' : '/chat']);
  }

  save(): void {
    if (this.form.invalid || this.saving() || !this.workspace.online()) { this.form.markAllAsTouched(); return; }
    this.saving.set(true);
    this.saved.set(false);
    this.saveError.set('');
    const value = this.form.getRawValue();
    this.form.disable({ emitEvent: false });
    const current = this.selectedAgent();
    this.api.updateAgent(this.selectedId(), {
      ...value,
      name: value.name.trim(),
      description: value.description.trim(),
      systemPrompt: value.systemPrompt.trim(),
      enabledTools: [...(current?.enabledTools ?? [])],
      ...(current?.ruleIds !== undefined ? { ruleIds: [...current.ruleIds] } : {}),
      ...(current?.knowledgeSourceIds !== undefined ? { knowledgeSourceIds: [...current.knowledgeSourceIds] } : {})
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: agent => {
        this.workspace.replaceAgent(agent);
        this.form.enable({ emitEvent: false });
        this.form.reset({ name: agent.name, description: agent.description, systemPrompt: agent.systemPrompt, providerId: agent.providerId, modelId: agent.modelId, temperature: agent.temperature, maxOutputTokens: agent.maxOutputTokens }, { emitEvent: false });
        this.saving.set(false);
        this.saved.set(true);
      },
      error: (error: unknown) => { this.form.enable({ emitEvent: false }); this.saveError.set(errorMessage(error)); this.saving.set(false); }
    });
  }
}
