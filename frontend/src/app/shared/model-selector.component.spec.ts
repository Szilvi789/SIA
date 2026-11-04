import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { Agent, AIModel, ModelSelection } from '../core/models';
import { ModelSelectorComponent } from './model-selector.component';

const agent: Agent = { id: 'writing-agent', name: 'Writer', description: 'Writing', systemPrompt: 'Write.', providerId: 'mock', modelId: 'default', temperature: 0.5, maxOutputTokens: 200, enabledTools: [] };
const models: AIModel[] = [
  { id: 'default', name: 'Default', providerId: 'mock', description: '', enabled: true, capabilities: ['TEXT', 'STRUCTURED_OUTPUT'], structuredOutput: true },
  { id: 'other', name: 'Other', providerId: 'local', description: '', enabled: true, capabilities: ['TEXT'] },
  { id: 'disabled', name: 'Disabled', providerId: 'mock', description: '', enabled: false }
];

describe('ModelSelectorComponent', () => {
  function setup() {
    const fixture = TestBed.createComponent(ModelSelectorComponent);
    fixture.componentRef.setInput('agent', agent);
    fixture.componentRef.setInput('models', models);
    fixture.componentRef.setInput('settings', { defaultModelId: 'other', tierMappings: { BALANCED: 'default' } });
    fixture.detectChanges();
    return fixture;
  }

  it('uses agent defaults, explains unmapped tiers, and never invents a model', () => {
    const fixture = setup();
    expect(fixture.componentInstance.effectiveModel()?.id).toBe('default');
    fixture.componentRef.setInput('selection', { overrideTier: 'ADVANCED' });
    fixture.detectChanges();
    expect(fixture.componentInstance.effectiveModel()).toBeUndefined();
    expect(fixture.nativeElement.textContent).toContain('This tier needs an enabled, compatible model');
  });

  it('emits temporary selections and restores default without editing the agent', () => {
    const fixture = setup();
    const selections: ModelSelection[] = [];
    fixture.componentInstance.selectionChange.subscribe(selection => selections.push(selection));
    fixture.componentInstance.choose('model:other');
    fixture.componentInstance.choose('tier:BALANCED');
    fixture.componentInstance.choose('default');
    expect(selections).toEqual([{ overrideModelId: 'other' }, { overrideTier: 'BALANCED' }, {}]);
    expect(agent.modelId).toBe('default');
  });

  it('filters disabled and incompatible models for Writing', () => {
    const fixture = setup();
    fixture.componentRef.setInput('requiredCapabilities', ['TEXT', 'STRUCTURED_OUTPUT']);
    fixture.detectChanges();
    expect(fixture.componentInstance.compatibleModels().map(model => model.id)).toEqual(['default']);
  });

  it('marks missing real pricing as unknown and shows mock usage without token claims', () => {
    const fixture = setup();
    expect(fixture.nativeElement.textContent).toContain('No API billing or measured token usage');
    fixture.componentRef.setInput('selection', { overrideModelId: 'other' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Estimated cost unknown');
  });

  it('binds cloud consent to the current provider and explicit knowledge sources', () => {
    const fixture = setup();
    fixture.componentRef.setInput('agent', { ...agent, knowledgeSourceIds: ['synthetic-source'] });
    fixture.componentRef.setInput('selection', { overrideModelId: 'other' });
    fixture.detectChanges();
    const selections: ModelSelection[] = [];
    fixture.componentInstance.selectionChange.subscribe(selection => selections.push(selection));
    expect(fixture.nativeElement.textContent).toContain('Local retrieval alone does not authorize cloud sharing');
    fixture.componentInstance.authorizeTask('local', true);
    expect(selections[0].dataSharingConsent).toEqual({ providerId: 'local', taskContent: true, personalInstructions: true, knowledgeSourceIds: [] });
    fixture.componentRef.setInput('selection', selections[0]);
    fixture.componentInstance.authorizeKnowledge('local', true);
    expect(selections[1].dataSharingConsent?.knowledgeSourceIds).toEqual(['synthetic-source']);
    fixture.componentInstance.choose('default');
    expect(selections[2].dataSharingConsent).toBeUndefined();
  });
});
