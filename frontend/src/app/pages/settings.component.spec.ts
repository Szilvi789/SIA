import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { SettingsComponent } from './settings.component';
import { ApiService } from '../core/api.service';
import { AIModel, GlobalModelSettings } from '../core/models';
import { WorkspaceService } from '../core/workspace.service';

describe('SettingsComponent model management', () => {
  const model: AIModel = {id: 'demo', providerId: 'mock', apiModelId: 'demo-api', name: 'Demo', description: 'Local model', enabled: true, capabilities: null, supportedParameters: null};
  const settings: GlobalModelSettings = {defaultModelId: 'demo', tierMappings: {BALANCED: 'demo'}};
  let api: {models: ReturnType<typeof vi.fn>; providers: ReturnType<typeof vi.fn>; modelSettings: ReturnType<typeof vi.fn>; usageSummary: ReturnType<typeof vi.fn>; updateModel: ReturnType<typeof vi.fn>; updateModelSettings: ReturnType<typeof vi.fn>; updateProvider: ReturnType<typeof vi.fn>; providerDataPermissions: ReturnType<typeof vi.fn>};

  beforeEach(async () => {
    api = {models: vi.fn(() => of([model])), providers: vi.fn(() => of([{id: 'mock', name: 'Mock', enabled: true, configured: true, adapterAvailable: true, mock: true}])), modelSettings: vi.fn(() => of(settings)), usageSummary: vi.fn(() => of([])), updateModel: vi.fn((_id: string, saved: AIModel) => of(saved)), updateModelSettings: vi.fn((saved: GlobalModelSettings) => of(saved)), updateProvider: vi.fn(), providerDataPermissions: vi.fn(() => of([]))};
    await TestBed.configureTestingModule({imports: [SettingsComponent], providers: [provideRouter([]), {provide: ApiService, useValue: api}, {provide: WorkspaceService, useValue: {models: signal<AIModel[]>([]), modelSettings: signal(settings), online: signal(true), runtimeConfig: signal({mode: 'demo', persistent: false})}}]}).compileComponents();
  });

  it('retains unknown metadata and prices when saving an existing model', () => {
    const fixture = TestBed.createComponent(SettingsComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.editModel(model);
    component.modelForm.controls.name.setValue('Renamed model');
    component.saveModel();
    expect(api.updateModel).toHaveBeenCalledWith('demo', expect.objectContaining({apiModelId: 'demo-api', name: 'Renamed model', capabilities: null, supportedParameters: null, reasoningEfforts: null, inputTokenPrice: null, outputTokenPrice: null, contextWindow: null, toolCalling: null}));
    expect(component.workspace.models()[0].name).toBe('Renamed model');
  });

  it('creates explicit metadata without turning unknown values into capabilities', () => {
    const fixture = TestBed.createComponent(SettingsComponent);
    const component = fixture.componentInstance;
    component.newModel();
    component.modelForm.patchValue({id: 'local-config', providerId: 'mock', apiModelId: 'actual-user-model', name: 'User model', capabilities: 'TEXT, STRUCTURED_OUTPUT, TEXT', structuredOutput: 'true', pricingUpdatedAt: '2026-11-04T00:00:00Z', supportedParameters: '[]', inputTokenPrice: '0', outputTokenPrice: '0.01', pricingUnit: '1000', currency: 'EUR', vision: 'false'});
    component.saveModel();
    expect(api.updateModel).toHaveBeenCalledWith('local-config', expect.objectContaining({capabilities: ['TEXT', 'STRUCTURED_OUTPUT'], supportedParameters: [], inputTokenPrice: 0, outputTokenPrice: 0.01, pricingUnit: 1000, currency: 'EUR', vision: false, embedding: null}));
    expect(component.workspace.models()).toHaveLength(2);
  });

  it('updates global tier mappings without changing any agent', () => {
    const fixture = TestBed.createComponent(SettingsComponent);
    const component = fixture.componentInstance;
    component.defaultsForm.patchValue({defaultModelId: '', ECONOMY: 'demo', BALANCED: ''});
    component.saveDefaults();
    expect(api.updateModelSettings).toHaveBeenCalledWith({defaultModelId: null, tierMappings: {ECONOMY: 'demo'}});
    expect(component.workspace.modelSettings().tierMappings).toEqual({ECONOMY: 'demo'});
  });

  it('keeps unsaved registry input when a save fails', () => {
    api.updateModel.mockReturnValue(throwError(() => new Error('unavailable')));
    const fixture = TestBed.createComponent(SettingsComponent);
    const component = fixture.componentInstance;
    component.editModel(model);
    component.modelForm.controls.description.setValue('My pending edit');
    component.saveModel();
    expect(component.error()).not.toBe('');
    expect(component.modelForm.controls.description.value).toBe('My pending edit');
    expect(component.busy()).toBe(false);
  });

  it('distinguishes unknown usage from free usage and mock from API calls', () => {
    api.usageSummary.mockReturnValue(of([{agentId: 'coding-agent', modelId: 'demo', providerId: 'mock', requests: 2, successfulRequests: 2, failedRequests: 0, mockRequests: 2, inputTokens: null, outputTokens: null, estimatedCost: null, currency: null}]));
    const fixture = TestBed.createComponent(SettingsComponent);
    fixture.detectChanges();
    const text = fixture.nativeElement.querySelector('.usage-table').textContent;
    expect(text).toContain('2 mock');
    expect(text).toContain('0 API');
    expect(text).toContain('Unknown / Unknown');
    expect(text).not.toContain('0.00');
  });
});
