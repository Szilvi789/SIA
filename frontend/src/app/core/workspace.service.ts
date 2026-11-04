import { computed, inject, Injectable, signal } from '@angular/core';
import { forkJoin } from 'rxjs';
import { ApiService, errorMessage } from './api.service';
import { Agent, AIModel, Conversation, GlobalModelSettings, Message, ModelSelection, RuntimeConfig } from './models';
import { canonicalAgentId, LEGACY_WRITING_AGENT_IDS, WRITING_AGENT_ID } from './agent-ids';
import { RuntimeContextService } from './runtime-context.service';

@Injectable({ providedIn: 'root' })
export class WorkspaceService {
  private readonly api = inject(ApiService);
  private readonly runtimeContext = inject(RuntimeContextService);
  readonly agents = signal<Agent[]>([]);
  readonly models = signal<AIModel[]>([]);
  readonly modelSettings = signal<GlobalModelSettings>({ defaultModelId: '', tierMappings: {} });
  readonly runtimeConfig = signal<RuntimeConfig>({ mode: 'demo', persistent: false, dataStorage: 'external', cloudRequestsMayLeaveDevice: true });
  readonly savedConversations = signal<Conversation[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly online = signal(false);
  readonly selectedAgentId = signal('coding-agent');
  readonly selectedModelId = signal('mock-balanced');
  readonly modelOverrides = signal<Record<string, ModelSelection>>({});
  readonly selectedModelSelection = computed(() => this.modelOverrides()[this.selectedAgentId()] ?? {});
  private readonly conversationIds = signal<Record<string, string>>({});
  readonly selectedConversationId = computed(() => this.conversationIds()[this.selectedAgentId()] ?? '');
  readonly conversations = signal<Record<string, Message[]>>({});
  readonly drafts = signal<Record<string, string>>({});
  readonly pending = signal<{ agentId: string; modelId: string; content: string } | null>(null);
  readonly sending = signal(false);
  private readonly chatErrors = signal<Record<string, string>>({});
  private writingReferencesMigrated = false;
  private loadedMode: RuntimeConfig['mode'] | null = null;
  readonly chatError = computed(() => this.chatErrors()[this.selectedAgentId()] ?? '');

  constructor() { this.load(); }

  load(): void {
    this.loading.set(true);
    this.error.set('');
    forkJoin({ agents: this.api.agents(), models: this.api.models(), health: this.api.health(), settings: this.api.modelSettings(), runtime: this.api.runtimeConfig() }).subscribe({
      next: ({ agents, models, health, settings, runtime }) => {
        if (this.loadedMode && this.loadedMode !== runtime.mode) {
          this.online.set(false); this.loading.set(false);
          this.error.set('The backend mode changed. Export any unsaved work, then reload to open the separate environment.');
          return;
        }
        this.loadedMode = runtime.mode;
        this.runtimeContext.mode.set(runtime.mode);
        this.runtimeConfig.set(runtime);
        this.agents.set(agents);
        this.models.set(models);
        this.modelSettings.set(settings);
        this.online.set(health.status === 'UP');
        this.migrateWritingReferences();
        if (!agents.some(agent => agent.id === this.selectedAgentId())) {
          this.selectedAgentId.set(agents[0]?.id ?? '');
        }
        this.refreshSelectedModel();
        if (runtime.mode === 'personal') this.restoreConversations();
        else this.loading.set(false);
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
    this.refreshSelectedModel();
  }

  openConversation(id: string): void {
    if (this.sending()) return;
    if (!id) { this.clearConversation(); return; }
    const saved = this.savedConversations().find(conversation => conversation.id === id);
    if (!saved) return;
    this.selectAgent(saved.agentId);
    this.conversations.update(conversations => ({ ...conversations, [saved.agentId]: saved.messages }));
    this.conversationIds.update(ids => ({ ...ids, [saved.agentId]: id }));
    this.selectModel({});
  }

  private restoreConversations(): void {
    this.api.conversations().subscribe({
      next: history => {
        this.savedConversations.set(history);
        for (const conversation of [...history].sort((a, b) => a.updatedAt.localeCompare(b.updatedAt))) {
          if (!this.agents().some(agent => agent.id === conversation.agentId)) continue;
          if ((this.conversations()[conversation.agentId]?.length ?? 0) > 0) continue;
          const latest = history.filter(item => item.agentId === conversation.agentId).sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))[0];
          this.conversations.update(conversations => ({ ...conversations, [conversation.agentId]: latest.messages }));
          this.conversationIds.update(ids => ({ ...ids, [conversation.agentId]: latest.id }));
        }
        this.loading.set(false);
      },
      error: (error: unknown) => { this.error.set(errorMessage(error)); this.loading.set(false); }
    });
  }

  replaceAgent(agent: Agent): void {
    this.agents.update(agents => agents.map(item => item.id === agent.id ? agent : item));
    if (agent.id === this.selectedAgentId()) this.refreshSelectedModel();
  }

  selectModel(selection: ModelSelection): void {
    if (this.sending()) return;
    this.modelOverrides.update(overrides => ({ ...overrides, [this.selectedAgentId()]: selection }));
    this.refreshSelectedModel();
  }

  private refreshSelectedModel(): void {
    const agent = this.agents().find(item => item.id === this.selectedAgentId());
    if (!agent) { this.selectedModelId.set(''); return; }
    const selection = this.selectedModelSelection();
    const tier = selection.tier || selection.overrideTier || agent?.modelSettings?.defaultTier;
    this.selectedModelId.set(selection.modelId || selection.overrideModelId || (tier
      ? agent?.modelSettings?.tierMappings?.[tier] || this.modelSettings().tierMappings[tier] || ''
      : agent?.modelId || this.modelSettings().defaultModelId || ''));
  }

  setDraft(content: string, agentId = this.selectedAgentId()): void {
    agentId = this.resolveAgentId(agentId);
    if (content !== (this.drafts()[agentId] ?? '')) this.modelOverrides.update(overrides => ({ ...overrides,
      [agentId]: { ...overrides[agentId], dataSharingConsent: undefined } }));
    this.drafts.update(drafts => ({ ...drafts, [agentId]: content }));
  }

  send(content: string): void {
    const message = content.trim();
    if (!message || this.sending() || !this.selectedAgentId()) return;
    const agentId = this.selectedAgentId();
    const modelId = this.selectedModelId();
    const selection = this.selectedModelSelection();
    // Request state lives with the workspace so navigation cannot discard a failed message.
    this.pending.set({ agentId, modelId, content });
    this.setDraft('', agentId);
    this.sending.set(true);
    this.chatErrors.update(errors => ({ ...errors, [agentId]: '' }));
    // Only store the exchange after the server accepts it, so a failed request can be retried safely.
    const conversationId = this.conversationIds()[agentId] ?? crypto.randomUUID();
    this.conversationIds.update(ids => ({ ...ids, [agentId]: conversationId }));
    this.api.chat({ agentId, ...selection, conversationId, message }).subscribe({
      next: response => {
        const user: Message = { id: `${response.id}-user`, role: 'user', content: message, createdAt: new Date().toISOString() };
        const assistant: Message = { id: response.id, role: 'assistant', content: response.content, createdAt: response.createdAt, modelId: response.modelId, providerId: response.providerId, source: response.source, execution: response.execution };
        const currentId = this.resolveAgentId(agentId);
        this.conversations.update(conversations => ({ ...conversations, [currentId]: [...(conversations[currentId] ?? []), user, assistant] }));
        if (this.runtimeConfig().mode === 'personal') this.savedConversations.update(history => [{ id: conversationId, agentId: currentId,
          messages: this.conversations()[currentId], updatedAt: response.createdAt }, ...history.filter(item => item.id !== conversationId)]);
        this.modelOverrides.update(overrides => ({ ...overrides, [agentId]: { ...overrides[agentId], dataSharingConsent: undefined } }));
        this.pending.set(null);
        this.sending.set(false);
      },
      error: (error: unknown) => {
        this.setDraft(content, agentId);
        this.modelOverrides.update(overrides => ({ ...overrides, [agentId]: { ...overrides[agentId], dataSharingConsent: undefined } }));
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
    this.conversationIds.update(ids => { const next = { ...ids }; delete next[this.selectedAgentId()]; return next; });
    this.selectModel({});
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
