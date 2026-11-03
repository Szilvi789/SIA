import { computed, inject, Injectable, signal } from '@angular/core';
import { forkJoin } from 'rxjs';
import { ApiService, errorMessage } from './api.service';
import { Agent, AIModel, Message } from './models';
import { canonicalAgentId, LEGACY_WRITING_AGENT_IDS, WRITING_AGENT_ID } from './agent-ids';

@Injectable({ providedIn: 'root' })
export class WorkspaceService {
  private readonly api = inject(ApiService);
  readonly agents = signal<Agent[]>([]);
  readonly models = signal<AIModel[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly online = signal(false);
  readonly selectedAgentId = signal('coding-agent');
  readonly selectedModelId = signal('mock-balanced');
  readonly conversations = signal<Record<string, Message[]>>({});
  readonly drafts = signal<Record<string, string>>({});
  readonly pending = signal<{ agentId: string; modelId: string; content: string } | null>(null);
  readonly sending = signal(false);
  private readonly chatErrors = signal<Record<string, string>>({});
  private writingReferencesMigrated = false;
  readonly chatError = computed(() => this.chatErrors()[this.selectedAgentId()] ?? '');

  constructor() { this.load(); }

  load(): void {
    this.loading.set(true);
    this.error.set('');
    forkJoin({ agents: this.api.agents(), models: this.api.models(), health: this.api.health() }).subscribe({
      next: ({ agents, models, health }) => {
        this.agents.set(agents);
        this.models.set(models);
        this.online.set(health.status === 'UP');
        this.migrateWritingReferences();
        if (!agents.some(agent => agent.id === this.selectedAgentId())) {
          this.selectedAgentId.set(agents[0]?.id ?? '');
        }
        if (!models.some(model => model.id === this.selectedModelId())) {
          this.selectedModelId.set(models[0]?.id ?? '');
        }
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.error.set(errorMessage(error));
        this.online.set(false);
        this.loading.set(false);
      }
    });
  }

  selectAgent(id: string): void {
    id = this.resolveAgentId(id);
    this.selectedAgentId.set(id);
    const agent = this.agents().find(item => item.id === id);
    if (agent) this.selectedModelId.set(agent.modelId);
  }

  replaceAgent(agent: Agent): void {
    this.agents.update(agents => agents.map(item => item.id === agent.id ? agent : item));
    if (agent.id === this.selectedAgentId()) this.selectedModelId.set(agent.modelId);
  }

  setDraft(content: string, agentId = this.selectedAgentId()): void {
    agentId = this.resolveAgentId(agentId);
    this.drafts.update(drafts => ({ ...drafts, [agentId]: content }));
  }

  send(content: string): void {
    const message = content.trim();
    if (!message || this.sending() || !this.selectedAgentId() || !this.selectedModelId()) return;
    const agentId = this.selectedAgentId();
    const modelId = this.selectedModelId();
    // Request state lives with the workspace so navigation cannot discard a failed message.
    this.pending.set({ agentId, modelId, content });
    this.setDraft('', agentId);
    this.sending.set(true);
    this.chatErrors.update(errors => ({ ...errors, [agentId]: '' }));
    // Only store the exchange after the server accepts it, so a failed request can be retried safely.
    this.api.chat({ agentId, modelId, message }).subscribe({
      next: response => {
        const user: Message = { id: `${response.id}-user`, role: 'user', content: message, createdAt: new Date().toISOString() };
        const assistant: Message = { id: response.id, role: 'assistant', content: response.content, createdAt: response.createdAt, modelId: response.modelId };
        const currentId = this.resolveAgentId(agentId);
        this.conversations.update(conversations => ({ ...conversations, [currentId]: [...(conversations[currentId] ?? []), user, assistant] }));
        this.pending.set(null);
        this.sending.set(false);
      },
      error: (error: unknown) => {
        this.setDraft(content, agentId);
        this.chatErrors.update(errors => ({ ...errors, [this.resolveAgentId(agentId)]: errorMessage(error) }));
        this.pending.set(null);
        this.sending.set(false);
      }
    });
  }

  clearConversation(): void {
    if (this.sending()) return;
    this.conversations.update(conversations => ({ ...conversations, [this.selectedAgentId()]: [] }));
    this.chatErrors.update(errors => ({ ...errors, [this.selectedAgentId()]: '' }));
  }

  private resolveAgentId(id: string): string {
    const canonical = canonicalAgentId(id);
    return this.agents().some(agent => agent.id === canonical) ? canonical : id;
  }

  private migrateWritingReferences(): void {
    if (!this.agents().some(agent => agent.id === WRITING_AGENT_ID)) return;
    const previousSelection = this.selectedAgentId();
    this.selectedAgentId.set(this.resolveAgentId(previousSelection));
    const pending = this.pending();
    if (pending) this.pending.set({ ...pending, agentId: this.resolveAgentId(pending.agentId) });
    if (this.writingReferencesMigrated) return;
    this.writingReferencesMigrated = true;
    const conversations = this.conversations();
    const all = [WRITING_AGENT_ID, ...LEGACY_WRITING_AGENT_IDS]
      .map(id => conversations[id] ?? []).filter(messages => messages.length)
      .sort((a, b) => a[0].createdAt.localeCompare(b[0].createdAt)).flat();
    if (all.length) {
      const unique = [...new Map(all.map(message => [message.id, message])).values()];
      // Preserve original alias keys for older in-memory references as well as the merged view.
      this.conversations.set({ ...conversations, [WRITING_AGENT_ID]: unique });
    }
    const drafts = this.drafts();
    if (drafts[WRITING_AGENT_ID] === undefined) {
      const recovered = drafts[previousSelection] || LEGACY_WRITING_AGENT_IDS.map(id => drafts[id]).find(Boolean);
      if (recovered) this.drafts.set({ ...drafts, [WRITING_AGENT_ID]: recovered });
    }
    const errors = this.chatErrors();
    if (errors[WRITING_AGENT_ID] === undefined && errors[previousSelection]) {
      this.chatErrors.set({ ...errors, [WRITING_AGENT_ID]: errors[previousSelection] });
    }
  }
}
