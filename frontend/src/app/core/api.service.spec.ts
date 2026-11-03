import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { API_BASE_URL, ApiService, errorMessage } from './api.service';
import { Agent, AIModel, ChatResponse } from './models';

const agent: Agent = {
  id: 'coding-agent', name: 'Coding Agent', description: 'Programming help',
  systemPrompt: 'Explain tradeoffs.', providerId: 'mock', modelId: 'mock-balanced',
  temperature: 0.3, maxOutputTokens: 2048, enabledTools: []
};
const models: AIModel[] = [{
  id: 'mock-balanced', name: 'Mock Balanced', providerId: 'mock', description: 'Demo model'
}];

describe('ApiService', () => {
  let api: ApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(ApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loads agent and model catalogs from the backend', async () => {
    const agentsResult = firstValueFrom(api.agents());
    const modelsResult = firstValueFrom(api.models());

    const agentsRequest = http.expectOne('/api/agents');
    expect(agentsRequest.request.method).toBe('GET');
    agentsRequest.flush([agent]);
    const modelsRequest = http.expectOne('/api/models');
    expect(modelsRequest.request.method).toBe('GET');
    modelsRequest.flush(models);

    expect(await agentsResult).toEqual([agent]);
    expect(await modelsResult).toEqual(models);
  });

  it('checks the backend health and mock provider status', async () => {
    const result = firstValueFrom(api.health());
    const request = http.expectOne('/api/health');
    expect(request.request.method).toBe('GET');
    request.flush({ status: 'UP', provider: 'mock' });

    expect(await result).toEqual({ status: 'UP', provider: 'mock' });
  });

  it('encodes an agent identifier as one URL path segment', async () => {
    const result = firstValueFrom(api.agent('agent/with spaces'));
    const request = http.expectOne('/api/agents/agent%2Fwith%20spaces');
    expect(request.request.method).toBe('GET');
    request.flush(agent);

    expect(await result).toEqual(agent);
  });

  it('saves editable configuration with PUT and returns the saved agent', async () => {
    const { id, ...configuration } = agent;
    const update = { ...configuration, name: 'Code reviewer', modelId: 'mock-precise', temperature: 0.1 };
    const saved = { ...update, id };
    const result = firstValueFrom(api.updateAgent(id, update));

    const request = http.expectOne('/api/agents/coding-agent');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(update);
    expect(request.request.body).not.toHaveProperty('id');
    request.flush(saved);

    expect(await result).toEqual(saved);
  });

  it('posts the selected agent, model, and message and preserves response metadata', async () => {
    const input = { agentId: 'coding-agent', modelId: 'mock-precise', message: 'Review this method' };
    const response: ChatResponse = {
      id: 'reply-1', agentId: input.agentId, modelId: input.modelId,
      content: 'Demo review', createdAt: '2026-01-15T12:00:00Z'
    };
    const result = firstValueFrom(api.chat(input));

    const request = http.expectOne('/api/chat');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(input);
    request.flush(response);

    expect(await result).toEqual(response);
  });

  it('propagates backend validation failures so callers can retain the draft', async () => {
    const result = firstValueFrom(api.chat({ agentId: 'coding-agent', modelId: 'unknown', message: 'Hello' }));
    const failure = expect(result).rejects.toMatchObject({
      status: 400, error: { detail: 'Select an available model.' }
    });
    http.expectOne('/api/chat').flush({ status: 400, detail: 'Select an available model.' }, {
      status: 400, statusText: 'Bad Request'
    });

    await failure;
  });
});

describe('ApiService base URL', () => {
  it('uses an injected backend address for a future desktop host', async () => {
    TestBed.configureTestingModule({ providers: [
      provideHttpClient(), provideHttpClientTesting(),
      { provide: API_BASE_URL, useValue: 'http://127.0.0.1:9090/api' }
    ] });
    const api = TestBed.inject(ApiService);
    const http = TestBed.inject(HttpTestingController);
    const result = firstValueFrom(api.health());
    http.expectOne('http://127.0.0.1:9090/api/health').flush({ status: 'UP', provider: 'mock' });

    expect((await result).status).toBe('UP');
    http.verify();
  });
});

describe('errorMessage', () => {
  it('explains how to recover when the local backend is unreachable', () => {
    expect(errorMessage(new HttpErrorResponse({ status: 0 }))).toContain('Start it on port 8080');
  });

  it('shows the backend ProblemDetail explanation', () => {
    const failure = new HttpErrorResponse({ status: 400, error: { detail: 'Select an available model.' } });
    expect(errorMessage(failure)).toBe('Select an available model.');
  });

  it('keeps a useful status when the server response has no explanation', () => {
    expect(errorMessage(new HttpErrorResponse({ status: 503, error: '<html>Unavailable</html>' })))
      .toContain('HTTP 503');
  });

  it('handles an unexpected client error without exposing its internals', () => {
    expect(errorMessage(new Error('internal implementation detail'))).toBe('Something went wrong. Please try again.');
  });
});
