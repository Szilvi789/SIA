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
import { Agent, AgentModelSettings, ModelParameters } from '../core/models';
import { IconComponent } from '../shared/icon.component';
import { agentIcon } from '../shared/agent-presentation';

@Component({
  standalone: true,
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, IconComponent],
  template: `
    <div class="page-title"><div><div class="eyebrow intro-label">A TEAM THAT THINKS YOUR WAY</div><h1>Your agents, your approach.</h1><p>Give each agent a purpose, a personality, and an independent model configuration.</p></div><span class="page-counter">{{workspace.agents().length}} PRESET AGENTS</span></div>
    <div class="agents-layout"><aside class="agent-list panel" aria-label="Select an agent"><div class="list-heading"><span>YOUR TEAM</span><span>{{workspace.agents().length}}</span></div>@for(agent of workspace.agents(); track agent.id){<button class="agent-list-item" type="button" [class.selected]="selectedId() === agent.id" [attr.aria-pressed]="selectedId() === agent.id" [disabled]="saving()" (click)="select(agent.id)"><span [class]="'agent-icon ' + agent.id"><app-icon [name]="iconFor(agent.id)" /></span><span><strong>{{agent.name}}</strong><small>{{agent.providerId}} · {{agent.modelId || 'Application default'}}</small></span><app-icon class="select-arrow" name="arrow" /></button>}@if(workspace.loading()){<p class="empty-loading">Loading agents…</p>}<div class="list-note"><app-icon name="spark" /><p>Start with a preset.<br>Make it a little more you.</p></div></aside>
    @if(selectedAgent(); as agent){<section class="agent-editor panel"><div class="editor-heading"><div><span class="eyebrow">AGENT CONFIGURATION</span><h2>{{agent.name}}</h2></div><button mat-stroked-button class="button-icon" (click)="chatWithAgent()" [disabled]="saving()"><app-icon name="chat" /> Try agent</button></div>
    <form [formGroup]="form" (ngSubmit)="save()"><div class="form-section"><div class="form-section-title"><span>01</span><div><h3>The essentials</h3><p>A clear name and purpose make a good first impression.</p></div></div><mat-form-field appearance="outline"><mat-label>Agent name</mat-label><input matInput formControlName="name" maxlength="80" required><mat-error>Enter a name of up to 80 characters.</mat-error></mat-form-field><mat-form-field appearance="outline"><mat-label>Description</mat-label><textarea matInput formControlName="description" rows="2" maxlength="500" required></textarea><mat-error>Enter a description of up to 500 characters.</mat-error></mat-form-field></div>
    <div class="form-section"><div class="form-section-title"><span>02</span><div><h3>Give it some direction</h3><p>Tell your agent how to think, respond, and help.</p></div></div><mat-form-field appearance="outline"><mat-label>System prompt</mat-label><textarea matInput class="prompt-input" formControlName="systemPrompt" rows="6" maxlength="8000" required></textarea><mat-error>Enter instructions of up to 8,000 characters.</mat-error></mat-form-field></div>
    <div class="form-section"><div class="form-section-title"><span>03</span><div><h3>Model defaults</h3><p>These settings belong to this agent. A default tier takes precedence over its default model.</p></div></div><div class="form-grid"><mat-form-field appearance="outline"><mat-label>Default provider</mat-label><mat-select formControlName="providerId" required>@for(provider of providerIds(); track provider){<mat-option [value]="provider">{{provider}}</mat-option>}</mat-select></mat-form-field><mat-form-field appearance="outline"><mat-label>Default model</mat-label><mat-select formControlName="modelId"><mat-option value="">Use application default</mat-option>@for(model of providerModels(); track model.id){<mat-option [value]="model.id" [disabled]="model.enabled === false">{{model.name}}{{model.enabled === false ? ' (disabled)' : ''}}</mat-option>}</mat-select><mat-hint>{{modelDescription()}}</mat-hint></mat-form-field><mat-form-field appearance="outline"><mat-label>Default tier</mat-label><mat-select formControlName="defaultTier"><mat-option value="">Use default model</mat-option>@for(tier of tiers; track tier){<mat-option [value]="tier">{{tier}}</mat-option>}</mat-select></mat-form-field><mat-form-field appearance="outline"><mat-label>Fallback model</mat-label><mat-select formControlName="fallbackModelId"><mat-option value="">No fallback configured</mat-option>@for(model of workspace.models(); track model.id){<mat-option [value]="model.id" [disabled]="model.enabled === false">{{model.name}} · {{model.providerId}}</mat-option>}</mat-select></mat-form-field></div><label class="checkbox-field"><input type="checkbox" formControlName="allowFallback"> Allow the configured fallback when the selected provider is unavailable</label>
    <details class="tier-details"><summary>Agent-specific tier mappings</summary><p class="model-note">Leave a mapping empty to use the application mapping.</p><div class="form-grid">@for(tier of tiers; track tier){<mat-form-field appearance="outline"><mat-label>{{tier}} model</mat-label><mat-select [formControlName]="tier"><mat-option value="">Use application mapping</mat-option>@for(model of workspace.models(); track model.id){<mat-option [value]="model.id" [disabled]="model.enabled === false">{{model.name}} · {{model.providerId}}</mat-option>}</mat-select></mat-form-field>}</div></details></div>
    <div class="form-section"><div class="form-section-title"><span>04</span><div><h3>Generation and cost controls</h3><p>Only parameters declared by the effective model are available.</p></div></div><p class="model-note">{{effectiveModel()?.name || 'Model configuration required'}} · {{effectiveModel()?.providerId || 'No provider selected'}}</p>@if(!effectiveModel()?.supportedParameters?.length){<p class="model-note">No supported generation controls are declared for this model. Configure verified metadata in Settings.</p>}<div class="form-grid">@if(supports('temperature')){<mat-form-field appearance="outline"><mat-label>Temperature</mat-label><input matInput type="number" min="0" max="2" step="0.1" formControlName="parameterTemperature"><mat-hint>Blank keeps the legacy agent default</mat-hint><mat-error>Use a value from 0 to 2.</mat-error></mat-form-field>}@if(supports('maxOutputTokens')){<mat-form-field appearance="outline"><mat-label>Maximum output tokens</mat-label><input matInput type="number" min="1" step="1" [attr.max]="effectiveModel()?.maxOutputTokens" formControlName="parameterMaxOutputTokens"><mat-hint>{{effectiveModel()?.maxOutputTokens ? 'Model limit: ' + effectiveModel()?.maxOutputTokens : 'Model limit unknown; blank keeps the legacy agent default'}}</mat-hint><mat-error>Enter a positive whole number within the model limit.</mat-error></mat-form-field>}@if(supports('topP')){<mat-form-field appearance="outline"><mat-label>Top-p</mat-label><input matInput type="number" min="0" max="1" step="any" formControlName="topP"><mat-error>Use a value greater than 0 and at most 1.</mat-error></mat-form-field>}@if(supports('reasoningEffort')){<mat-form-field appearance="outline"><mat-label>Reasoning effort</mat-label><mat-select formControlName="reasoningEffort"><mat-option value="">Provider default</mat-option>@for(effort of effectiveModel()?.reasoningEfforts ?? []; track effort){<mat-option [value]="effort">{{effort}}</mat-option>}</mat-select></mat-form-field>}@if(supports('timeoutMs')){<mat-form-field appearance="outline"><mat-label>Timeout (milliseconds)</mat-label><input matInput type="number" min="100" max="300000" step="1" formControlName="timeoutMs"><mat-error>Enter a whole number from 100 to 300,000.</mat-error></mat-form-field>}@if(supports('tools') && effectiveModel()?.toolCalling === true){<mat-form-field appearance="outline"><mat-label>Tool permissions</mat-label><input matInput formControlName="parameterTools"><mat-hint>Comma-separated subset of existing enabled tool identifiers</mat-hint></mat-form-field>}<mat-form-field appearance="outline"><mat-label>Maximum estimated cost per request</mat-label><input matInput type="number" min="0" step="0.001" formControlName="maxEstimatedCost"><mat-hint>Blank means no request cost limit</mat-hint><mat-error>Enter a nonnegative amount.</mat-error></mat-form-field><mat-form-field appearance="outline"><mat-label>Guardrail currency</mat-label><input matInput formControlName="currency" maxlength="12"><mat-hint>Must match configured model pricing</mat-hint></mat-form-field></div><label class="checkbox-field"><input type="checkbox" formControlName="allowUnknownCost"> Allow requests with unknown cost, even when a maximum is set</label><p class="model-note">Cost estimates depend on configured pricing and reported usage. They are not exact billing amounts.</p></div>
    <div class="save-area">@if(saveError()){<div class="error-message" role="alert">{{saveError()}}</div>}<div class="save-actions"><span class="save-status" role="status">@if(saved()){<app-icon name="check" /> {{workspace.runtimeConfig().persistent ? 'Changes saved in your private database.' : 'Changes saved for this demo session.'}}}@else if(form.dirty){You have unsaved changes.}@else{Configuration is up to date.}</span><button mat-flat-button type="submit" class="button-icon" [disabled]="form.invalid || !form.dirty || saving() || !workspace.online()">@if(saving()){Saving…}@else{<app-icon name="check" /> Save changes}</button></div></div></form></section>}
    @else if(!workspace.loading()){<div class="panel empty-loading">No agent configuration is available. Reconnect to the backend to load your team.</div>}</div>
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
  readonly tiers = ['ECONOMY', 'BALANCED', 'ADVANCED'] as const;
  private displayedAgentId = '';
  readonly form = this.fb.group({
    name: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(80)]], description: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(500)]], systemPrompt: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(8000)]], providerId: ['mock', Validators.required], modelId: ['mock-balanced'],
    temperature: [0.7, [Validators.required, Validators.min(0), Validators.max(2)]], maxOutputTokens: [2048, [Validators.required, Validators.min(64), Validators.max(8192), Validators.pattern(/^\d+$/)]],
    defaultTier: '', fallbackModelId: '', allowFallback: false, ECONOMY: '', BALANCED: '', ADVANCED: '',
    parameterTemperature: this.fb.control<number | null>(null, [Validators.min(0), Validators.max(2)]), parameterMaxOutputTokens: this.fb.control<number | null>(null, [Validators.min(1), Validators.max(1_000_000), Validators.pattern(/^\d+$/)]), topP: this.fb.control<number | null>(null, [Validators.min(Number.MIN_VALUE), Validators.max(1)]), reasoningEffort: '', timeoutMs: this.fb.control<number | null>(null, [Validators.min(100), Validators.max(300_000), Validators.pattern(/^\d+$/)]), parameterTools: '', maxEstimatedCost: this.fb.control<number | null>(null, Validators.min(0)), currency: '', allowUnknownCost: true
  });

  constructor() {
    effect(() => {
      const agents = this.workspace.agents();
      if (!agents.some(agent => agent.id === this.selectedId()) && agents.length > 0) this.selectedId.set(agents[0].id);
      const agent = this.selectedAgent();
      if (agent && (agent.id !== this.displayedAgentId || !this.form.dirty)) { this.loadAgent(agent); this.displayedAgentId = agent.id; }
    });
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.saved.set(false));
    this.form.controls.providerId.valueChanges.pipe(takeUntilDestroyed()).subscribe(providerId => {
      if (this.form.controls.modelId.value && !this.workspace.models().some(model => model.id === this.form.controls.modelId.value && model.providerId === providerId)) this.form.controls.modelId.setValue('');
    });
  }

  providerIds(): string[] { return [...new Set([...this.workspace.models().map(model => model.providerId), this.form.controls.providerId.value])].filter(Boolean); }
  providerModels() { return this.workspace.models().filter(model => model.providerId === this.form.controls.providerId.value); }
  modelDescription(): string { return this.workspace.models().find(model => model.id === this.form.controls.modelId.value)?.description ?? 'Application default when no model is selected'; }
  effectiveModel() {
    const value = this.form.getRawValue();
    const tier = value.defaultTier;
    const mapping = this.tiers.includes(tier as typeof this.tiers[number]) ? value[tier as typeof this.tiers[number]] : '';
    const modelId = tier ? mapping || this.workspace.modelSettings?.().tierMappings[tier] : value.modelId || this.workspace.modelSettings?.().defaultModelId;
    return this.workspace.models().find(model => model.id === modelId);
  }
  supports(parameter: keyof ModelParameters): boolean { return this.effectiveModel()?.supportedParameters?.includes(parameter) === true; }
  select(id: string): void { if (!this.saving()) { this.selectedId.set(id); this.saved.set(false); this.saveError.set(''); } }
  chatWithAgent(): void { this.workspace.selectAgent(this.selectedId()); void this.router.navigate([this.selectedId() === 'writing-agent' ? '/writing' : '/chat']); }

  private loadAgent(agent: Agent): void {
    const settings = agent.modelSettings;
    const params = settings?.parameters;
    this.form.reset({name: agent.name, description: agent.description, systemPrompt: agent.systemPrompt, providerId: agent.providerId, modelId: agent.modelId ?? '', temperature: agent.temperature, maxOutputTokens: agent.maxOutputTokens, defaultTier: settings?.defaultTier ?? '', fallbackModelId: settings?.fallbackModelId ?? '', allowFallback: settings?.allowFallback ?? false, ECONOMY: settings?.tierMappings['ECONOMY'] ?? '', BALANCED: settings?.tierMappings['BALANCED'] ?? '', ADVANCED: settings?.tierMappings['ADVANCED'] ?? '', parameterTemperature: params?.temperature === undefined ? agent.temperature : params.temperature, parameterMaxOutputTokens: params?.maxOutputTokens === undefined ? agent.maxOutputTokens : params.maxOutputTokens, topP: params?.topP ?? null, reasoningEffort: params?.reasoningEffort ?? '', timeoutMs: params?.timeoutMs ?? null, parameterTools: params?.tools?.join(', ') ?? '', maxEstimatedCost: settings?.maxEstimatedCost ?? null, currency: settings?.currency ?? '', allowUnknownCost: settings?.allowUnknownCost ?? true}, {emitEvent: false});
  }

  save(): void {
    if (this.form.invalid || this.saving() || !this.workspace.online()) { this.form.markAllAsTouched(); return; }
    const value = this.form.getRawValue();
    const current = this.selectedAgent();
    const tierMappings = {...current?.modelSettings?.tierMappings};
    for (const tier of this.tiers) { if (value[tier]) tierMappings[tier] = value[tier]; else delete tierMappings[tier]; }
    const parameters: ModelParameters = {};
    if (this.supports('temperature') && value.parameterTemperature !== null) parameters.temperature = value.parameterTemperature;
    if (this.supports('maxOutputTokens') && value.parameterMaxOutputTokens !== null) parameters.maxOutputTokens = value.parameterMaxOutputTokens;
    if (this.supports('topP') && value.topP !== null) parameters.topP = value.topP;
    if (this.supports('reasoningEffort') && value.reasoningEffort) parameters.reasoningEffort = value.reasoningEffort;
    if (this.supports('timeoutMs') && value.timeoutMs !== null) parameters.timeoutMs = value.timeoutMs;
    if (this.supports('tools') && this.effectiveModel()?.toolCalling === true && value.parameterTools.trim()) parameters.tools = [...new Set(value.parameterTools.split(',').map(tool => tool.trim()).filter(Boolean))];
    if (parameters.maxOutputTokens && this.effectiveModel()?.maxOutputTokens && parameters.maxOutputTokens > this.effectiveModel()!.maxOutputTokens!) {this.saveError.set('Maximum output tokens exceeds the configured model limit.'); return;}
    const modelSettings: AgentModelSettings = {defaultTier: value.defaultTier || null, fallbackModelId: value.fallbackModelId || null, allowFallback: value.allowFallback, tierMappings, parameters, maxEstimatedCost: value.maxEstimatedCost, currency: value.currency.trim() || null, allowUnknownCost: value.allowUnknownCost};
    this.saving.set(true); this.saved.set(false); this.saveError.set(''); this.form.disable({emitEvent: false});
    this.api.updateAgent(this.selectedId(), {name: value.name.trim(), description: value.description.trim(), systemPrompt: value.systemPrompt.trim(), providerId: value.providerId, modelId: value.modelId, temperature: value.temperature, maxOutputTokens: value.maxOutputTokens, modelSettings, enabledTools: [...(current?.enabledTools ?? [])], ...(current?.ruleIds !== undefined ? {ruleIds: [...current.ruleIds]} : {}), ...(current?.knowledgeSourceIds !== undefined ? {knowledgeSourceIds: [...current.knowledgeSourceIds]} : {})}).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: agent => {this.workspace.replaceAgent(agent); this.form.enable({emitEvent: false}); this.loadAgent(agent); this.saving.set(false); this.saved.set(true);},
      error: (error: unknown) => {this.form.enable({emitEvent: false}); this.saveError.set(errorMessage(error)); this.saving.set(false);}
    });
  }
}
