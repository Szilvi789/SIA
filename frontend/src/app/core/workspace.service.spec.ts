import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { ApiService } from './api.service';
import { Agent, AIModel, ChatResponse } from './models';
import { WorkspaceService } from './workspace.service';

const coding: Agent = {
  id: 'coding-agent', name: 'Coding Agent', description: 'Programming help',
  systemPrompt: 'Explain tradeoffs.', providerId: 'mock', modelId: 'mock-balanced',
  temperature: 0.3, maxOutputTokens: 2048, enabledTools: []
};
const translation: Agent = {
  ...coding, id: 'translation-agent', name: 'Translation Agent', modelId: 'mock-precise'
};
const models: AIModel[] = [
  { id: 'mock-balanced', name: 'Mock Balanced', providerId: 'mock', description: 'Balanced demo' },
  { id: 'mock-precise', name: 'Mock Precise', providerId: 'mock', description: 'Concise demo' }
];

describe('WorkspaceService', () => {
  let workspace: WorkspaceService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    workspace = TestBed.inject(WorkspaceService);
  });

  afterEach(() => http.verify());

  it('keeps model overrides within a conversation and resolves defaults on the backend', () => {
    finishLoad();
    workspace.selectModel({ overrideModelId: 'mock-precise' });
    workspace.send('First task');
    const first = http.expectOne('/api/chat');
    expect(first.request.body).toMatchObject({ overrideModelId: 'mock-precise', conversationId: expect.any(String) });
    expect(first.request.body.modelId).toBeUndefined();
    const conversationId = first.request.body.conversationId;
    first.flush(reply());
    workspace.selectAgent('translation-agent');
    expect(workspace.selectedModelSelection()).toEqual({});
    workspace.selectAgent('coding-agent');
    expect(workspace.selectedModelSelection()).toEqual({ overrideModelId: 'mock-precise' });
    workspace.clearConversation();
    expect(workspace.selectedModelSelection()).toEqual({});
    workspace.send('A new conversation');
    const next = http.expectOne('/api/chat');
    expect(next.request.body.conversationId).not.toBe(conversationId);
    expect(next.request.body.overrideModelId).toBeUndefined();
    expect(workspace.agents()[0].modelId).toBe('mock-balanced');
    next.flush(reply());
  });

  it('does not overwrite a conversation override when agent defaults are saved', () => {
    finishLoad();
    workspace.selectModel({ overrideModelId: 'mock-precise' });
    workspace.replaceAgent({ ...coding, name: 'Updated default' });
    expect(workspace.selectedModelId()).toBe('mock-precise');
  });

  function finishLoad(agents: Agent[] = [coding, translation], availableModels: AIModel[] = models): void {
    http.expectOne('/api/agents').flush(agents);
    http.expectOne('/api/models').flush(availableModels);
    http.expectOne('/api/model-settings').flush({defaultModelId: 'mock-balanced', tierMappings: {ECONOMY: 'mock-precise', BALANCED: 'mock-balanced'}});
    http.expectOne('/api/health').flush({ status: 'UP', provider: 'mock' });
  }

  function reply(id = 'reply-1', agentId = 'coding-agent', modelId = 'mock-balanced'): ChatResponse {
    return { id, agentId, modelId, content: `Demo response ${id}`, createdAt: '2026-01-15T12:00:00Z' };
  }

  it('keeps the workspace loading until catalogs and health have all arrived', () => {
    expect(workspace.loading()).toBe(true);
    expect(workspace.online()).toBe(false);
    http.expectOne('/api/agents').flush([coding, translation]);
    http.expectOne('/api/models').flush(models);
    expect(workspace.loading()).toBe(true);
    http.expectOne('/api/model-settings').flush({defaultModelId: 'mock-balanced', tierMappings: {ECONOMY: 'mock-precise', BALANCED: 'mock-balanced'}});
    http.expectOne('/api/health').flush({ status: 'UP', provider: 'mock' });

    expect(workspace.loading()).toBe(false);
    expect(workspace.online()).toBe(true);
    expect(workspace.error()).toBe('');
    expect(workspace.agents()).toEqual([coding, translation]);
    expect(workspace.models()).toEqual(models);
  });

  it('reports a failed connection and can recover when the backend starts', () => {
    http.expectOne('/api/agents').flush([coding]);
    http.expectOne('/api/models').flush(models);
    http.expectOne('/api/model-settings').flush({defaultModelId: 'mock-balanced', tierMappings: {ECONOMY: 'mock-precise', BALANCED: 'mock-balanced'}});
    http.expectOne('/api/health').error(new ProgressEvent('error'));

    expect(workspace.loading()).toBe(false);
    expect(workspace.online()).toBe(false);
    expect(workspace.error()).toContain('local backend is unavailable');

    workspace.load();
    expect(workspace.loading()).toBe(true);
    expect(workspace.error()).toBe('');
    finishLoad();
    expect(workspace.online()).toBe(true);
    expect(workspace.loading()).toBe(false);
  });

  it('selects available defaults when previously selected identifiers disappear', () => {
    finishLoad([translation], [models[1]]);

    expect(workspace.selectedAgentId()).toBe('translation-agent');
    expect(workspace.selectedModelId()).toBe('mock-precise');
  });

  it('preserves valid agent and model selections when reloading catalogs', () => {
    finishLoad();
    workspace.selectAgent('translation-agent');
    workspace.load();
    finishLoad();

    expect(workspace.selectedAgentId()).toBe('translation-agent');
    expect(workspace.selectedModelId()).toBe('mock-precise');
  });

  it('reflects a successfully saved agent without replacing other configurations', () => {
    finishLoad();
    const saved: Agent = { ...coding, name: 'Code reviewer', modelId: 'mock-precise', temperature: 0.1 };
    const { id, ...update } = saved;
    TestBed.inject(ApiService).updateAgent(id, update).subscribe(agent => workspace.replaceAgent(agent));

    expect(workspace.agents()[0].name).toBe('Coding Agent');
    http.expectOne('/api/agents/coding-agent').flush(saved);

    expect(workspace.agents()).toEqual([saved, translation]);
    expect(workspace.selectedModelId()).toBe('mock-precise');
    workspace.selectAgent('translation-agent');
    expect(workspace.selectedModelId()).toBe('mock-precise');
  });

  it('stores an accepted exchange under the agent that sent it even if selection changes', () => {
    finishLoad();
    workspace.send('   ');
    http.expectNone('/api/chat');
    workspace.send('  Review this method  ');
    expect(workspace.sending()).toBe(true);
    expect(workspace.conversations()).toEqual({});
    workspace.send('An accidental second click');
    const request = http.expectOne('/api/chat');
    expect(request.request.body).toEqual({
      agentId: 'coding-agent', conversationId: expect.any(String), message: 'Review this method'
    });

    workspace.selectAgent('translation-agent');
    request.flush(reply());

    expect(workspace.sending()).toBe(false);
    expect(workspace.conversations()['coding-agent']).toEqual([
      expect.objectContaining({ role: 'user', content: 'Review this method' }),
      expect.objectContaining({ id: 'reply-1', role: 'assistant', content: 'Demo response reply-1', modelId: 'mock-balanced' })
    ]);
    expect(workspace.conversations()['translation-agent']).toBeUndefined();
    http.expectNone('/api/chat');
  });

  it('retains no partial exchange after failure and permits a clean retry', () => {
    finishLoad();
    workspace.send('Help with this method');
    http.expectOne('/api/chat').flush({}, { status: 503, statusText: 'Service Unavailable' });

    expect(workspace.sending()).toBe(false);
    expect(workspace.chatError()).toContain('HTTP 503');
    expect(workspace.conversations()).toEqual({});

    workspace.send('Help with this method');
    expect(workspace.chatError()).toBe('');
    http.expectOne('/api/chat').flush(reply());
    expect(workspace.conversations()['coding-agent']).toHaveLength(2);
    expect(workspace.sending()).toBe(false);
  });

  it('does not erase conversation history while a response is pending', () => {
    finishLoad();
    workspace.send('First message');
    http.expectOne('/api/chat').flush(reply());
    workspace.send('Follow-up');
    workspace.clearConversation();
    expect(workspace.conversations()['coding-agent']).toHaveLength(2);
    http.expectOne('/api/chat').flush(reply('reply-2'));
    expect(workspace.conversations()['coding-agent']).toHaveLength(4);

    workspace.clearConversation();
    expect(workspace.conversations()['coding-agent']).toEqual([]);
    expect(workspace.chatError()).toBe('');
  });

  it('keeps drafts separate for each agent and clears only the draft being submitted', () => {
    finishLoad();
    workspace.setDraft('Review this implementation');
    workspace.selectAgent('translation-agent');
    workspace.setDraft('Translate this introduction');
    workspace.selectAgent('coding-agent');

    expect(workspace.drafts()['coding-agent']).toBe('Review this implementation');
    workspace.send(workspace.drafts()['coding-agent']);

    expect(workspace.drafts()['coding-agent']).toBe('');
    expect(workspace.drafts()['translation-agent']).toBe('Translate this introduction');
    expect(workspace.pending()).toEqual({
      agentId: 'coding-agent', modelId: 'mock-balanced', content: 'Review this implementation'
    });
    http.expectOne('/api/chat').flush(reply());
    expect(workspace.pending()).toBeNull();
  });

  it('restores a failed draft to its original agent after navigation changes the selection', () => {
    finishLoad();
    const original = '  Keep this original\nmessage for retry  ';
    workspace.setDraft(original);
    workspace.send(original);
    workspace.selectAgent('translation-agent');
    workspace.setDraft('An independent translation draft');
    http.expectOne('/api/chat').flush({}, { status: 503, statusText: 'Service Unavailable' });

    expect(workspace.drafts()['coding-agent']).toBe(original);
    expect(workspace.drafts()['translation-agent']).toBe('An independent translation draft');
    expect(workspace.chatError()).toBe('');
    expect(workspace.pending()).toBeNull();
    expect(workspace.conversations()).toEqual({});

    workspace.selectAgent('coding-agent');
    expect(workspace.chatError()).toContain('HTTP 503');
    workspace.send(workspace.drafts()['coding-agent']);
    const retry = http.expectOne('/api/chat');
    expect(retry.request.body.message).toBe(original.trim());
    retry.flush(reply());
    expect(workspace.chatError()).toBe('');
    expect(workspace.drafts()['coding-agent']).toBe('');
    expect(workspace.conversations()['coding-agent']).toHaveLength(2);
  });

  it('ignores empty messages and cannot send when catalogs offer no selections', () => {
    finishLoad([], []);
    workspace.send('   ');
    workspace.send('Hello');

    expect(workspace.selectedAgentId()).toBe('');
    expect(workspace.selectedModelId()).toBe('');
    expect(workspace.sending()).toBe(false);
    http.expectNone('/api/chat');
  });

  it('migrates legacy writing references once while retaining original histories and draft keys', () => {
    const original = [
      { id: 'old-user', role: 'user' as const, content: 'A translation request', createdAt: '2026-11-03T10:00:01Z' },
      { id: 'old-reply', role: 'assistant' as const, content: 'A reply', createdAt: '2026-11-03T10:00:00Z' }
    ];
    workspace.selectedAgentId.set('translation-agent');
    workspace.conversations.set({ 'translation-agent': original });
    workspace.drafts.set({ 'translation-agent': 'An unfinished translation', 'email-assistant': 'An unfinished email' });
    const writing: Agent = { ...coding, id: 'writing-agent', name: 'Writing & Communication Agent' };
    finishLoad([coding, writing]);

    expect(workspace.selectedAgentId()).toBe('writing-agent');
    expect(workspace.conversations()['writing-agent']).toEqual(original);
    expect(workspace.conversations()['translation-agent']).toEqual(original);
    expect(workspace.drafts()['writing-agent']).toBe('An unfinished translation');
    expect(workspace.drafts()['email-assistant']).toBe('An unfinished email');
    workspace.selectAgent('email-assistant');
    expect(workspace.selectedAgentId()).toBe('writing-agent');

    workspace.clearConversation();
    workspace.setDraft('');
    workspace.load();
    finishLoad([coding, writing]);
    expect(workspace.conversations()['writing-agent']).toEqual([]);
    expect(workspace.drafts()['writing-agent']).toBe('');
  });
});
