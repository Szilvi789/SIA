import { DecimalPipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { forkJoin } from 'rxjs';
import { ApiService, errorMessage } from '../core/api.service';
import { AIModel, GlobalModelSettings, ProviderStatus, UsageSummary } from '../core/models';
import { WorkspaceService } from '../core/workspace.service';
import { IconComponent } from '../shared/icon.component';
import { ProviderPrivacyComponent } from '../shared/provider-privacy.component';

@Component({
  standalone: true,
  imports: [RouterLink, ReactiveFormsModule, DecimalPipe, MatButtonModule, MatFormFieldModule, MatInputModule, MatSelectModule, IconComponent, ProviderPrivacyComponent],
  template: `
    <div class="page-title"><div><div class="eyebrow intro-label">THE DETAILS BEHIND YOUR WORKSPACE</div><h1>Your models, thoughtfully connected.</h1><p>Configure the models your agents can use, their defaults, and approximate usage.</p></div></div>
    @if(error()){<div class="error-message" role="alert">{{error()}}</div>}
    <section class="panel management-panel"><div class="panel-heading"><span class="setting-icon"><app-icon name="globe" /></span><div><h2>Your providers</h2><p>API {{api.baseUrl}} · {{workspace.online() ? 'Backend connected' : 'Backend unavailable'}}</p></div><button mat-stroked-button type="button" (click)="refresh()" [disabled]="loading()">Refresh</button></div>
      <p class="section-note">Credentials are configured on the server. Enabling a provider requires a configured adapter before it can run a model.</p>
      <div class="provider-list">@for(provider of providers(); track provider.id){<div class="provider-row"><div><strong>{{provider.name}}</strong><small>{{provider.id}} · {{provider.mock ? 'Mock responses' : provider.adapterAvailable ? 'API adapter available' : 'Adapter not implemented'}} · {{provider.configured ? 'Configured' : 'Configuration required'}}</small></div><span class="status-pill" [class.offline]="!provider.enabled || !provider.configured || !provider.adapterAvailable">{{provider.enabled ? 'Enabled' : 'Disabled'}}</span><button mat-stroked-button type="button" (click)="toggleProvider(provider)" [disabled]="busy()">{{provider.enabled ? 'Disable' : 'Enable'}}</button></div>}@empty{<p class="section-note">{{loading() ? 'Loading provider status…' : 'Provider status is unavailable.'}}</p>}</div>
    </section>
    <section class="panel management-panel"><div class="panel-heading"><span class="setting-icon"><app-icon name="spark" /></span><div><h2>Application defaults and tiers</h2><p>Tiers are your own model mappings. Unmapped tiers require configuration.</p></div></div>
      <form [formGroup]="defaultsForm" (ngSubmit)="saveDefaults()"><div class="management-grid defaults-grid"><mat-form-field appearance="outline"><mat-label>Application default model</mat-label><mat-select formControlName="defaultModelId"><mat-option value="">No default configured</mat-option>@for(model of workspace.models(); track model.id){<mat-option [value]="model.id" [disabled]="model.enabled === false">{{model.name}} · {{model.providerId}}</mat-option>}</mat-select></mat-form-field>@for(tier of tiers; track tier){<mat-form-field appearance="outline"><mat-label>{{tier}} tier</mat-label><mat-select [formControlName]="tier"><mat-option value="">Configuration required</mat-option>@for(model of workspace.models(); track model.id){<mat-option [value]="model.id" [disabled]="model.enabled === false">{{model.name}}</mat-option>}</mat-select></mat-form-field>}</div><div class="form-actions"><span class="section-note">Agent-specific tier mappings take precedence.</span><button mat-flat-button type="submit" [disabled]="busy() || loading() || defaultsForm.pristine">Save defaults</button></div></form>
    </section>
    <section class="panel management-panel"><div class="panel-heading"><span class="setting-icon"><app-icon name="settings" /></span><div><h2>Model registry</h2><p>Model configuration IDs stay stable when names or API model IDs change.</p></div><button mat-stroked-button type="button" (click)="newModel()" [disabled]="busy()">Add model</button></div>
      <div class="registry-layout"><div class="registry-list" aria-label="Registered models">@for(model of workspace.models(); track model.id){<button type="button" [class.selected]="editingId() === model.id" (click)="editModel(model)" [disabled]="busy()"><strong>{{model.name}}</strong><small>{{model.providerId}} · {{model.enabled === false ? 'Disabled' : 'Enabled'}}</small><small>{{model.id}}</small></button>}@empty{<p class="section-note">No models configured.</p>}</div>
      <form class="registry-editor" [formGroup]="modelForm" (ngSubmit)="saveModel()"><h3>{{editingId() ? 'Edit model' : 'Add a model configuration'}}</h3><div class="management-grid"><mat-form-field appearance="outline"><mat-label>Configuration ID</mat-label><input matInput formControlName="id" [readonly]="!!editingId()" required><mat-hint>Unique internal reference</mat-hint></mat-form-field><mat-form-field appearance="outline"><mat-label>Provider ID</mat-label><input matInput formControlName="providerId" required><mat-hint>Use a provider identifier listed above</mat-hint></mat-form-field><mat-form-field appearance="outline"><mat-label>API model ID</mat-label><input matInput formControlName="apiModelId" required></mat-form-field><mat-form-field appearance="outline"><mat-label>Display name</mat-label><input matInput formControlName="name" required></mat-form-field><mat-form-field appearance="outline" class="full-width"><mat-label>Description</mat-label><textarea matInput formControlName="description" rows="2"></textarea></mat-form-field><mat-form-field appearance="outline"><mat-label>Availability</mat-label><mat-select formControlName="enabled"><mat-option [value]="true">Enabled</mat-option><mat-option [value]="false">Disabled</mat-option></mat-select></mat-form-field><mat-form-field appearance="outline"><mat-label>Capabilities</mat-label><input matInput formControlName="capabilities"><mat-hint>TEXT, STRUCTURED_OUTPUT, TOOLS, VISION, EMBEDDING · blank unknown, [] none</mat-hint></mat-form-field></div>
      <details class="metadata-details"><summary>Capabilities, generation parameters, and pricing</summary><p class="section-note">Enter only verified model metadata. Blank values remain unknown. Prices are estimates per the configured token unit.</p><div class="management-grid">@for(field of numericFields; track field.key){<mat-form-field appearance="outline"><mat-label>{{field.label}}</mat-label><input matInput type="number" min="0" [formControlName]="field.key"><mat-error>Enter a valid nonnegative value.</mat-error></mat-form-field>}<mat-form-field appearance="outline"><mat-label>Currency</mat-label><input matInput formControlName="currency" placeholder="e.g. USD"></mat-form-field><mat-form-field appearance="outline"><mat-label>Pricing updated at (ISO timestamp)</mat-label><input matInput formControlName="pricingUpdatedAt" placeholder="YYYY-MM-DDTHH:mm:ssZ"></mat-form-field><mat-form-field appearance="outline" class="full-width"><mat-label>Supported parameters</mat-label><input matInput formControlName="supportedParameters"><mat-hint>maxOutputTokens, temperature, topP, reasoningEffort, timeoutMs, tools · blank unknown, [] none</mat-hint></mat-form-field><mat-form-field appearance="outline" class="full-width"><mat-label>Supported reasoning efforts</mat-label><input matInput formControlName="reasoningEfforts"><mat-hint>Comma-separated provider values; blank unknown</mat-hint></mat-form-field>@for(flag of featureFlags; track flag.key){<mat-form-field appearance="outline"><mat-label>{{flag.label}}</mat-label><mat-select [formControlName]="flag.key"><mat-option value="">Unknown</mat-option><mat-option value="true">Supported</mat-option><mat-option value="false">Unsupported</mat-option></mat-select></mat-form-field>}</div></details>
      <div class="form-actions"><span class="section-note">Disable a model to retain existing agent and usage references.</span><button mat-flat-button type="submit" [disabled]="busy() || modelForm.invalid">{{busy() ? 'Saving…' : 'Save model'}}</button></div></form></div>
    </section>
    <section class="panel management-panel"><div class="panel-heading"><span class="setting-icon"><app-icon name="shield" /></span><div><h2>Approximate usage</h2><p>Per agent and model for this backend session. Estimates are not billing statements.</p></div></div><div class="usage-scroll"><table class="usage-table"><thead><tr><th>Agent / model</th><th>Requests</th><th>Tokens in / out</th><th>Estimated cost</th></tr></thead><tbody>@for(row of usage(); track $index){<tr><td><strong>{{row.agentId || 'Unassigned'}}</strong><small>{{row.modelId}} · {{row.providerId}}</small><small>{{row.mockRequests}} mock · {{row.requests - row.mockRequests}} API · {{row.failedRequests}} failed</small></td><td>{{row.requests}}</td><td>{{row.inputTokens === null ? 'Unknown' : row.inputTokens}} / {{row.outputTokens === null ? 'Unknown' : row.outputTokens}}</td><td>{{row.estimatedCost === null ? 'Unknown' : (row.estimatedCost | number:'1.2-8')}} {{row.currency || ''}}</td></tr>}@empty{<tr><td colspan="4">No usage recorded yet.</td></tr>}</tbody></table></div></section>
    <app-provider-privacy [personal]="workspace.runtimeConfig().mode === 'personal'" />
    <p class="section-note persistence-note">@if(workspace.runtimeConfig().persistent){Personal mode stores configurations, private rules, drafts, conversations, and usage in your private PostgreSQL database. Files use a separate directory outside the repository.}@else{Demo mode uses synthetic configurations and temporary backend memory. No personal database is connected.} Export downloads use your browser's selected destination; choose a private folder outside the repository.</p>
    <p class="save-notice" role="status">{{notice()}}</p><div class="settings-footer"><span>Shape each agent's defaults.</span><a routerLink="/agents">Configure your agents <app-icon name="arrow" /></a></div>
  `,
  styleUrl: './settings.component.scss'
})
export class SettingsComponent {
  readonly api = inject(ApiService);
  readonly workspace = inject(WorkspaceService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly fb = inject(FormBuilder).nonNullable;
  readonly providers = signal<ProviderStatus[]>([]);
  readonly usage = signal<UsageSummary[]>([]);
  readonly loading = signal(false);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly notice = signal('');
  readonly editingId = signal('');
  readonly tiers = ['ECONOMY', 'BALANCED', 'ADVANCED'] as const;
  readonly numericFields = [{key: 'contextWindow', label: 'Context window'}, {key: 'maxOutputTokens', label: 'Maximum output token limit'}, {key: 'inputTokenPrice', label: 'Input token price'}, {key: 'outputTokenPrice', label: 'Output token price'}, {key: 'pricingUnit', label: 'Pricing token unit'}];
  readonly featureFlags = [{key: 'toolCalling', label: 'Tool calling'}, {key: 'structuredOutput', label: 'Structured output'}, {key: 'vision', label: 'Vision'}, {key: 'embedding', label: 'Embedding'}];
  readonly defaultsForm = this.fb.group({defaultModelId: '', ECONOMY: '', BALANCED: '', ADVANCED: ''});
  readonly modelForm = this.fb.group({
    id: ['', [Validators.required, Validators.pattern(/^[A-Za-z0-9][A-Za-z0-9._-]{0,79}$/)]], providerId: ['', [Validators.required, Validators.pattern(/\S/)]], apiModelId: ['', [Validators.required, Validators.pattern(/\S/)]], name: ['', [Validators.required, Validators.pattern(/\S/)]], description: '', enabled: true,
    capabilities: '', contextWindow: ['', [Validators.min(1), Validators.pattern(/^\d*$/)]], maxOutputTokens: ['', [Validators.min(1), Validators.pattern(/^\d*$/)]], inputTokenPrice: ['', Validators.min(0)], outputTokenPrice: ['', Validators.min(0)], currency: '', pricingUnit: ['', [Validators.min(1), Validators.pattern(/^\d*$/)]], pricingUpdatedAt: '', supportedParameters: '', reasoningEfforts: '', toolCalling: '', structuredOutput: '', vision: '', embedding: ''
  });

  constructor() { this.refresh(); }

  refresh(): void {
    this.loading.set(true); this.error.set('');
    forkJoin({providers: this.api.providers(), settings: this.api.modelSettings(), usage: this.api.usageSummary(), models: this.api.models()}).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: ({providers, settings, usage, models}) => {
        this.providers.set(providers); this.usage.set(usage); this.workspace.models.set(models); this.workspace.modelSettings.set(settings);
        if (!this.defaultsForm.dirty) this.loadDefaults(settings);
        this.loading.set(false);
      }, error: error => {this.error.set(errorMessage(error)); this.loading.set(false);}
    });
  }

  private loadDefaults(settings: GlobalModelSettings): void { this.defaultsForm.reset({defaultModelId: settings.defaultModelId ?? '', ECONOMY: settings.tierMappings['ECONOMY'] ?? '', BALANCED: settings.tierMappings['BALANCED'] ?? '', ADVANCED: settings.tierMappings['ADVANCED'] ?? ''}); }

  toggleProvider(provider: ProviderStatus): void {
    this.busy.set(true); this.error.set('');
    this.api.updateProvider(provider.id, !provider.enabled).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({next: updated => { this.providers.update(list => list.map(item => item.id === updated.id ? updated : item)); this.busy.set(false); this.notice.set(`${updated.name} ${updated.enabled ? 'enabled' : 'disabled'}.`); }, error: error => this.failed(error)});
  }

  saveDefaults(): void {
    if (this.busy()) return;
    const value = this.defaultsForm.getRawValue();
    const tierMappings = {...this.workspace.modelSettings().tierMappings};
    for (const tier of this.tiers) { if(value[tier]) tierMappings[tier] = value[tier]; else delete tierMappings[tier]; }
    this.busy.set(true); this.error.set('');
    this.api.updateModelSettings({defaultModelId: value.defaultModelId || null, tierMappings}).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({next: settings => {this.workspace.modelSettings.set(settings); this.loadDefaults(settings); this.busy.set(false); this.notice.set('Application defaults saved.');}, error: error => this.failed(error)});
  }

  newModel(): void { this.editingId.set(''); this.modelForm.reset(); this.notice.set(''); }

  editModel(model: AIModel): void {
    this.editingId.set(model.id);
    const list = (value: string[] | null | undefined) => value == null ? '' : value.length ? value.join(', ') : '[]';
    const flag = (value: boolean | null | undefined) => value == null ? '' : String(value);
    this.modelForm.reset({id: model.id, providerId: model.providerId, apiModelId: model.apiModelId ?? '', name: model.name, description: model.description, enabled: model.enabled !== false, capabilities: list(model.capabilities), contextWindow: model.contextWindow?.toString() ?? '', maxOutputTokens: model.maxOutputTokens?.toString() ?? '', inputTokenPrice: model.inputTokenPrice?.toString() ?? '', outputTokenPrice: model.outputTokenPrice?.toString() ?? '', currency: model.currency ?? '', pricingUnit: model.pricingUnit?.toString() ?? '', pricingUpdatedAt: model.pricingUpdatedAt ?? '', supportedParameters: list(model.supportedParameters), reasoningEfforts: list(model.reasoningEfforts), toolCalling: flag(model.toolCalling), structuredOutput: flag(model.structuredOutput), vision: flag(model.vision), embedding: flag(model.embedding)});
  }

  saveModel(): void {
    if (this.modelForm.invalid || this.busy()) {this.modelForm.markAllAsTouched(); return;}
    const value = this.modelForm.getRawValue();
    const numeric = (text: string | number | null | undefined): number | null => text == null || text === '' ? null : Number(text);
    const list = (text: string): string[] | null => !text.trim() ? null : text.trim() === '[]' ? [] : [...new Set(text.split(',').map(item => item.trim()).filter(Boolean))];
    const flag = (text: string): boolean | null => text === '' ? null : text === 'true';
    if(value.pricingUpdatedAt && Number.isNaN(Date.parse(value.pricingUpdatedAt))) {this.error.set('Enter a valid ISO pricing timestamp.'); return;}
    const model: AIModel = {...value, id: value.id.trim(), providerId: value.providerId.trim(), apiModelId: value.apiModelId.trim(), name: value.name.trim(), description: value.description.trim(), capabilities: list(value.capabilities), contextWindow: numeric(value.contextWindow), maxOutputTokens: numeric(value.maxOutputTokens), inputTokenPrice: numeric(value.inputTokenPrice), outputTokenPrice: numeric(value.outputTokenPrice), pricingUnit: numeric(value.pricingUnit), currency: value.currency.trim() || null, pricingUpdatedAt: value.pricingUpdatedAt ? new Date(value.pricingUpdatedAt).toISOString() : null, supportedParameters: list(value.supportedParameters), reasoningEfforts: list(value.reasoningEfforts), toolCalling: flag(value.toolCalling), structuredOutput: flag(value.structuredOutput), vision: flag(value.vision), embedding: flag(value.embedding)};
    this.busy.set(true); this.error.set('');
    this.api.updateModel(model.id, model).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({next: saved => {this.workspace.models.update(models => models.some(item => item.id === saved.id) ? models.map(item => item.id === saved.id ? saved : item) : [...models, saved]); this.editModel(saved); this.busy.set(false); this.notice.set(`${saved.name} saved.`);}, error: error => this.failed(error)});
  }

  private failed(error: unknown): void { this.error.set(errorMessage(error)); this.busy.set(false); }
}
