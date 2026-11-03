import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ChatComponent } from './chat.component';
import { WorkspaceService } from '../core/workspace.service';
import { Agent } from '../core/models';

const coding: Agent = {
  id: 'coding-agent', name: 'Coding Agent', description: 'Programming and debugging help',
  systemPrompt: 'Explain tradeoffs.', providerId: 'mock', modelId: 'mock-balanced',
  temperature: 0.3, maxOutputTokens: 2048, enabledTools: []
};
const translation: Agent = { ...coding, id: 'translation-agent', name: 'Translation Agent' };
const response = {
  id: 'reply-1', agentId: 'coding-agent', modelId: 'mock-balanced',
  content: 'A mock answer', createdAt: '2026-01-15T12:00:00Z'
};

describe('ChatComponent', () => {
  let workspace: WorkspaceService;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ChatComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()]
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    workspace = TestBed.inject(WorkspaceService);
    http.expectOne('/api/agents').flush([coding, translation]);
    http.expectOne('/api/models').flush([{
      id: 'mock-balanced', name: 'Mock Balanced', providerId: 'mock', description: 'Demo model'
    }]);
    http.expectOne('/api/health').flush({ status: 'UP', provider: 'mock' });
  });

  afterEach(() => http.verify());

  it('renders selectors, honest mock mode messaging, and the empty conversation', () => {
    const fixture = TestBed.createComponent(ChatComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('What are we working on?');
    expect(fixture.nativeElement.textContent).toContain('Mock provider');
    expect(fixture.nativeElement.textContent).toContain('No real AI requests are made.');
    expect(fixture.nativeElement.querySelectorAll('mat-select')).toHaveLength(2);
  });

  it('disables sending whitespace and submits a message with the keyboard', () => {
    const fixture = TestBed.createComponent(ChatComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    const send = vi.spyOn(workspace, 'send');
    component.updateDraft('   ');
    component.submit();
    expect(send).not.toHaveBeenCalled();
    component.updateDraft('Explain a Java interface');
    const event = new KeyboardEvent('keydown', { key: 'Enter', cancelable: true });
    component.onKeydown(event);
    fixture.detectChanges();

    expect(event.defaultPrevented).toBe(true);
    expect(send).toHaveBeenCalledExactlyOnceWith('Explain a Java interface');
    expect(component.draft()).toBe('');
    expect(fixture.nativeElement.textContent).toContain('Explain a Java interface');
    expect(fixture.nativeElement.textContent).toContain('Your agent is preparing a mock response');
    http.expectOne('/api/chat').flush(response);
  });

  it('allows Shift+Enter and composition without sending prematurely', () => {
    const fixture = TestBed.createComponent(ChatComponent);
    fixture.detectChanges();
    fixture.componentInstance.updateDraft('A multiline message');
    const newline = new KeyboardEvent('keydown', { key: 'Enter', shiftKey: true, cancelable: true });
    const composition = new KeyboardEvent('keydown', { key: 'Enter', isComposing: true, cancelable: true });
    fixture.componentInstance.onKeydown(newline);
    fixture.componentInstance.onKeydown(composition);

    expect(newline.defaultPrevented).toBe(false);
    expect(composition.defaultPrevented).toBe(false);
    http.expectNone('/api/chat');
  });

  it('restores a failed message after the chat view is destroyed and recreated', () => {
    const original = TestBed.createComponent(ChatComponent);
    original.detectChanges();
    original.componentInstance.updateDraft('Please keep this draft');
    original.componentInstance.submit();
    const request = http.expectOne('/api/chat');
    original.destroy();

    const returned = TestBed.createComponent(ChatComponent);
    returned.detectChanges();
    expect(returned.nativeElement.textContent).toContain('Please keep this draft');
    request.flush({}, { status: 503, statusText: 'Service Unavailable' });
    returned.detectChanges();

    expect(returned.componentInstance.draft()).toBe('Please keep this draft');
    expect(returned.nativeElement.querySelector('[role="alert"]').textContent).toContain('HTTP 503');
    returned.componentInstance.submit();
    http.expectOne('/api/chat').flush(response);
    expect(workspace.conversations()['coding-agent']).toHaveLength(2);
  });

  it('shows a pending message only in the conversation that submitted it', () => {
    const fixture = TestBed.createComponent(ChatComponent);
    fixture.detectChanges();
    fixture.componentInstance.updateDraft('A coding-only request');
    fixture.componentInstance.submit();
    workspace.selectAgent('translation-agent');
    fixture.detectChanges();

    expect(fixture.componentInstance.pendingMessage()).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('A coding-only request');
    expect(fixture.nativeElement.querySelector('.thinking')).toBeNull();
    workspace.selectAgent('coding-agent');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('A coding-only request');
    http.expectOne('/api/chat').flush(response);
  });

  it('renders returned content as plain text without executing HTML', () => {
    const fixture = TestBed.createComponent(ChatComponent);
    workspace.conversations.set({
      'coding-agent': [{ id: 'message-1', role: 'assistant', content: '<script>unsafe()</script>', createdAt: '2026-11-03T10:00:00Z' }]
    });
    fixture.detectChanges();
    const history: HTMLElement = fixture.nativeElement.querySelector('.message-area');
    expect(history.textContent).toContain('<script>unsafe()</script>');
    expect(history.querySelector('script')).toBeNull();
  });
});
