import { Component, computed, effect, ElementRef, inject, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { WorkspaceService } from '../core/workspace.service';
import { IconComponent } from '../shared/icon.component';
import { agentIcon } from '../shared/agent-presentation';
import { ModelSelectorComponent } from '../shared/model-selector.component';

@Component({
  standalone: true,
  imports: [FormsModule, DatePipe, RouterLink, MatButtonModule, MatFormFieldModule, MatSelectModule, IconComponent, ModelSelectorComponent],
  template: `
    <div class="page-title"><div><h1>A little conversation. A new direction.</h1><p>Choose an agent and give your idea somewhere to begin.</p></div><button mat-stroked-button class="button-icon" [disabled]="workspace.sending() || messages().length === 0" (click)="workspace.clearConversation()"><app-icon name="plus" /> New conversation</button></div>
    <section class="chat-panel panel" aria-label="Agent conversation"><div class="chat-toolbar"><div class="selectors"><mat-form-field appearance="outline" subscriptSizing="dynamic"><mat-label>Agent</mat-label><mat-select [value]="workspace.selectedAgentId()" (selectionChange)="workspace.selectAgent($event.value)" [disabled]="workspace.loading() || workspace.sending()">@for(agent of workspace.agents(); track agent.id){<mat-option [value]="agent.id">{{agent.name}}</mat-option>}</mat-select></mat-form-field><app-model-selector [agent]="selectedAgent()" [models]="workspace.models()" [settings]="workspace.modelSettings()" [selection]="workspace.selectedModelSelection()" [disabled]="workspace.loading() || workspace.sending()" scope="conversation" (selectionChange)="workspace.selectModel($event)" /></div><span class="mock-indicator"><span></span> Mock provider</span></div>
      <div class="message-area" aria-label="Message history" aria-live="polite" aria-relevant="additions" #messageArea>
        @if(messages().length === 0 && !pendingMessage()) {
          <div class="chat-empty"><span [class]="'agent-icon large ' + workspace.selectedAgentId()"><app-icon [name]="iconFor(workspace.selectedAgentId())" /></span><span class="eyebrow">YOUR THOUGHT PARTNER IS READY</span><h2>What are we working on?</h2><p>{{selectedAgent()?.description || 'Choose an agent above to get started.'}}</p><div class="suggestions">@for(prompt of suggestions(); track prompt){<button type="button" (click)="updateDraft(prompt); composer.focus()" [disabled]="workspace.loading() || workspace.sending() || !workspace.online()"><app-icon name="plus" />{{prompt}}</button>}</div></div>
        }
        @for(message of messages(); track message.id){<article class="message" [class.user-message]="message.role === 'user'"><span class="message-avatar">@if(message.role === 'user'){Y}@else{<app-icon name="spark" />}</span><div class="message-body"><div class="message-meta"><strong>{{message.role === 'user' ? 'You' : selectedAgent()?.name}}</strong><span>{{message.createdAt | date:'shortTime'}}</span>@if(message.role === 'assistant'){<span class="response-tag">MOCK RESPONSE</span>}</div><p>{{message.content}}</p>@if(message.role === 'assistant' && message.modelId){<small class="response-model">{{message.providerId || 'mock'}} · {{message.modelId}}@if(message.execution?.fallbackUsed){ · Configured fallback used}</small>}</div></article>}
        @if(pendingMessage(); as pending) {<article class="message user-message"><span class="message-avatar">Y</span><div class="message-body"><div class="message-meta"><strong>You</strong><span>Sending…</span></div><p>{{pending.content}}</p></div></article><div class="thinking" role="status"><app-icon name="spark" /><span>Your agent is preparing a mock response</span><span class="thinking-dots">···</span></div>}
      </div>
      <div class="composer-area">@if(workspace.chatError()){<div class="error-message" role="alert">{{workspace.chatError()}} Your message is still in the input below.</div>}<form class="composer" (ngSubmit)="submit()"><label class="sr-only" for="chat-message">Message your agent</label><textarea #composer id="chat-message" name="message" [ngModel]="draft()" (ngModelChange)="updateDraft($event)" [disabled]="workspace.sending() || workspace.loading() || !workspace.online()" placeholder="Give your idea a place to start…" rows="2" maxlength="12000" (keydown)="onKeydown($event)"></textarea><div class="composer-bottom"><span><app-icon name="shield" /><span>Local session <span class="input-hint">· Enter to send, Shift + Enter for a new line</span></span></span><button mat-flat-button type="submit" [disabled]="!draft().trim() || workspace.sending() || !workspace.online() || !workspace.selectedAgentId()" aria-label="Send message"><app-icon name="send" /></button></div></form><p class="chat-disclaimer">Mock responses demonstrate the workflow. No real AI requests are made. <a routerLink="/settings">About this workspace</a></p></div>
    </section>
  `,
  styleUrl: './chat.component.scss'
})
export class ChatComponent {
  readonly workspace = inject(WorkspaceService);
  private readonly messageArea = viewChild<ElementRef<HTMLElement>>('messageArea');
  readonly draft = computed(() => this.workspace.drafts()[this.workspace.selectedAgentId()] ?? '');
  readonly pendingMessage = computed(() => {
    const pending = this.workspace.pending();
    return pending?.agentId === this.workspace.selectedAgentId() ? pending : null;
  });
  readonly iconFor = agentIcon;
  readonly selectedAgent = computed(() => this.workspace.agents().find(agent => agent.id === this.workspace.selectedAgentId()));
  readonly messages = computed(() => this.workspace.conversations()[this.workspace.selectedAgentId()] ?? []);
  readonly suggestions = computed(() => {
    switch (this.workspace.selectedAgentId()) {
      case 'translation-agent': return ['Translate a short message into German', 'Make this translation sound more natural'];
      case 'email-assistant': return ['Draft a friendly project follow-up', 'Help me write a concise introduction'];
      case 'documentation-agent': return ['Outline a README for my project', 'Explain an API endpoint clearly'];
      default: return ['Help me think through a coding problem', 'Review an approach before I start building'];
    }
  });

  constructor() {
    effect(() => {
      const history = this.messageArea()?.nativeElement;
      this.messages();
      this.pendingMessage();
      if (history) queueMicrotask(() => {
        if (history.isConnected) history.scrollTop = history.scrollHeight;
      });
    });
    const initialMessage = inject(ActivatedRoute).snapshot.queryParamMap.get('message');
    if (initialMessage && !this.draft() && !this.pendingMessage()) this.updateDraft(initialMessage.slice(0, 12000));
  }

  updateDraft(content: string): void { this.workspace.setDraft(content); }

  submit(): void {
    if (!this.draft().trim() || this.workspace.sending() || !this.workspace.online()) return;
    this.workspace.send(this.draft());
  }

  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) { event.preventDefault(); this.submit(); }
  }
}
