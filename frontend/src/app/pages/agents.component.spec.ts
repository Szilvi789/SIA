import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Subject } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AgentsComponent } from './agents.component';
import { ApiService } from '../core/api.service';
import { Agent } from '../core/models';
import { WorkspaceService } from '../core/workspace.service';

describe('AgentsComponent', () => {
  const agent: Agent = {
    id: 'coding-agent', name: 'Coding Agent', description: 'Programming help', systemPrompt: 'Explain clearly.',
    providerId: 'mock', modelId: 'mock-balanced', temperature: 0.7, maxOutputTokens: 2048, enabledTools: []
  };
  let response: Subject<Agent>;
  let updateAgent: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    response = new Subject<Agent>();
    updateAgent = vi.fn().mockReturnValue(response);
    const agents = signal<Agent[]>([{ ...agent }]);
    await TestBed.configureTestingModule({
      imports: [AgentsComponent], providers: [provideRouter([]),
        { provide: ApiService, useValue: { updateAgent } },
        { provide: WorkspaceService, useValue: {
          agents, models: signal([{ id: 'mock-balanced', name: 'Mock Balanced', providerId: 'mock', description: 'Balanced demo responses' }]),
          loading: signal(false), online: signal(true), selectAgent: vi.fn(),
          replaceAgent: (updated: Agent) => agents.set([updated])
        } }
      ]
    }).compileComponents();
  });

  it('loads the selected preset into the editable form', () => {
    const fixture = TestBed.createComponent(AgentsComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.form.getRawValue()).toMatchObject({ name: agent.name, temperature: 0.7, maxOutputTokens: 2048 });
    expect(fixture.nativeElement.querySelector('input[formControlName="name"]').value).toBe('Coding Agent');
  });

  it('rejects blank names and fractional output tokens before calling the API', () => {
    const fixture = TestBed.createComponent(AgentsComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.form.patchValue({ name: '   ', maxOutputTokens: 64.5 });
    component.save();
    expect(component.form.invalid).toBe(true);
    expect(component.form.controls.name.touched).toBe(true);
    expect(updateAgent).not.toHaveBeenCalled();
  });

  it('saves edits through the API and shows the server-confirmed configuration', () => {
    const fixture = TestBed.createComponent(AgentsComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.form.patchValue({ name: '  My coding helper  ', temperature: 0.2, maxOutputTokens: 1024 });
    component.form.markAsDirty();
    component.save();
    expect(updateAgent).toHaveBeenCalledExactlyOnceWith('coding-agent', expect.objectContaining({ name: 'My coding helper', temperature: 0.2, maxOutputTokens: 1024, enabledTools: [] }));
    expect(component.saving()).toBe(true);
    expect(component.form.disabled).toBe(true);
    response.next({ ...agent, name: 'My coding helper', temperature: 0.2, maxOutputTokens: 1024 });
    response.complete();
    fixture.detectChanges();
    expect(component.saving()).toBe(false);
    expect(component.form.enabled).toBe(true);
    expect(component.form.pristine).toBe(true);
    expect(component.saved()).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Changes saved for this backend session.');
    expect(fixture.nativeElement.querySelector('h2').textContent).toContain('My coding helper');
  });

  it('keeps unsaved input available after a server error', () => {
    const fixture = TestBed.createComponent(AgentsComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.form.controls.name.setValue('My unsaved helper');
    component.form.markAsDirty();
    component.save();
    response.error(new Error('offline'));
    fixture.detectChanges();
    expect(component.saveError()).not.toBe('');
    expect(component.saving()).toBe(false);
    expect(component.form.controls.name.value).toBe('My unsaved helper');
    expect(component.form.dirty).toBe(true);
    expect(component.form.enabled).toBe(true);
    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
  });

  it('preserves module assignments when the existing editor saves other settings', () => {
    const workspace = TestBed.inject(WorkspaceService);
    const assigned: Agent = {
      ...agent,
      ruleIds: ['coding-rules', 'documentation-guidelines'],
      knowledgeSourceIds: ['architecture-notes'],
      enabledTools: ['future-approved-tool']
    };
    workspace.agents.set([assigned]);
    const fixture = TestBed.createComponent(AgentsComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.form.controls.name.setValue('My configured agent');
    component.form.markAsDirty();

    component.save();

    expect(updateAgent).toHaveBeenCalledExactlyOnceWith('coding-agent', expect.objectContaining({
      name: 'My configured agent',
      ruleIds: assigned.ruleIds,
      knowledgeSourceIds: assigned.knowledgeSourceIds,
      enabledTools: assigned.enabledTools
    }));
    response.next({ ...assigned, name: 'My configured agent' });
    response.complete();
    fixture.detectChanges();
    expect(workspace.agents()[0].ruleIds).toEqual(assigned.ruleIds);
    expect(workspace.agents()[0].knowledgeSourceIds).toEqual(assigned.knowledgeSourceIds);
    expect(workspace.agents()[0].enabledTools).toEqual(assigned.enabledTools);
  });

  it('saves independent tier, fallback authorization and model-specific parameters', () => {
    const workspace = TestBed.inject(WorkspaceService);
    workspace.models.set([
      {id: 'mock-balanced', name: 'Balanced', providerId: 'mock', description: '', enabled: true, supportedParameters: ['maxOutputTokens', 'reasoningEffort'], reasoningEfforts: ['high'], maxOutputTokens: 32000},
      {id: 'mock-economy', name: 'Economy', providerId: 'mock', description: '', enabled: true, supportedParameters: []}
    ]);
    const fixture = TestBed.createComponent(AgentsComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.form.patchValue({defaultTier: 'ADVANCED', ADVANCED: 'mock-balanced', fallbackModelId: 'mock-economy', allowFallback: false, parameterMaxOutputTokens: 16000, parameterTemperature: 1.5, reasoningEffort: 'high', maxEstimatedCost: 0.02, currency: 'EUR', allowUnknownCost: false});
    component.form.markAsDirty();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('input[formControlName="parameterTemperature"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('input[formControlName="parameterMaxOutputTokens"]')).not.toBeNull();
    component.save();
    expect(updateAgent).toHaveBeenCalledWith('coding-agent', expect.objectContaining({
      temperature: 0.7, maxOutputTokens: 2048,
      modelSettings: {defaultTier: 'ADVANCED', tierMappings: {ADVANCED: 'mock-balanced'}, fallbackModelId: 'mock-economy', allowFallback: false, parameters: {maxOutputTokens: 16000, reasoningEffort: 'high'}, maxEstimatedCost: 0.02, currency: 'EUR', allowUnknownCost: false}
    }));
  });

  it('clears a mismatched model when choosing another provider', () => {
    const workspace = TestBed.inject(WorkspaceService);
    workspace.models.update(models => [...models, {id: 'local-user-model', name: 'Local', providerId: 'local', description: ''}]);
    const fixture = TestBed.createComponent(AgentsComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.form.controls.providerId.setValue('local');
    expect(component.form.controls.modelId.value).toBe('');
    expect(component.providerModels().map(model => model.id)).toEqual(['local-user-model']);
    expect(workspace.agents()[0].modelId).toBe('mock-balanced');
  });

  it('blocks an output-token default above the configured model limit', () => {
    const workspace = TestBed.inject(WorkspaceService);
    workspace.models.set([{id: 'mock-balanced', name: 'Balanced', providerId: 'mock', description: '', supportedParameters: ['maxOutputTokens'], maxOutputTokens: 1000}]);
    const fixture = TestBed.createComponent(AgentsComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.controls.parameterMaxOutputTokens.setValue(2000);
    fixture.componentInstance.save();
    expect(updateAgent).not.toHaveBeenCalled();
    expect(fixture.componentInstance.saveError()).toContain('model limit');
  });

  it('rejects zero top-p and timeouts outside the adapter range', () => {
    const fixture = TestBed.createComponent(AgentsComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.form.patchValue({topP: 0, timeoutMs: 99});
    expect(component.form.controls.topP.invalid).toBe(true);
    expect(component.form.controls.timeoutMs.invalid).toBe(true);
    component.save();
    expect(updateAgent).not.toHaveBeenCalled();
    component.form.patchValue({topP: 0.5, timeoutMs: 300001});
    expect(component.form.controls.timeoutMs.invalid).toBe(true);
    component.form.controls.timeoutMs.setValue(300000);
    expect(component.form.valid).toBe(true);
  });
});
