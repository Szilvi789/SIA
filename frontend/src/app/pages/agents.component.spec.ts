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
});
