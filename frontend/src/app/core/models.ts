export interface Agent {
  id: string;
  name: string;
  description: string;
  systemPrompt: string;
  providerId: string;
  modelId: string;
  temperature: number;
  maxOutputTokens: number;
  enabledTools: string[];
  /** Optional on older backend responses; omitted updates preserve existing assignments. */
  ruleIds?: string[];
  knowledgeSourceIds?: string[];
  modelSettings?: AgentModelSettings | null;
}

export type AgentUpdate = Omit<Agent, 'id'>;

export interface AIModel {
  id: string;
  name: string;
  providerId: string;
  description: string;
  apiModelId?: string;
  enabled?: boolean;
  capabilities?: string[] | null;
  contextWindow?: number | null;
  maxOutputTokens?: number | null;
  inputTokenPrice?: number | null;
  outputTokenPrice?: number | null;
  currency?: string | null;
  pricingUnit?: number | null;
  pricingUpdatedAt?: string | null;
  supportedParameters?: string[] | null;
  reasoningEfforts?: string[] | null;
  toolCalling?: boolean | null;
  structuredOutput?: boolean | null;
  vision?: boolean | null;
  embedding?: boolean | null;
}

export interface ModelParameters {
  maxOutputTokens?: number | null;
  temperature?: number | null;
  topP?: number | null;
  reasoningEffort?: string | null;
  timeoutMs?: number | null;
  tools?: string[] | null;
}

export interface AgentModelSettings {
  defaultTier?: string | null;
  fallbackModelId?: string | null;
  allowFallback: boolean;
  tierMappings: Record<string, string>;
  parameters: ModelParameters;
  maxEstimatedCost?: number | null;
  currency?: string | null;
  allowUnknownCost: boolean;
}

export interface ModelSelection {
  dataSharingConsent?: DataSharingConsent | null;
  modelId?: string | null;
  tier?: string | null;
  overrideModelId?: string | null;
  overrideTier?: string | null;
  parameters?: ModelParameters | null;
  allowFallback?: boolean | null;
  conversationId?: string | null;
}

export interface GlobalModelSettings { defaultModelId: string | null; tierMappings: Record<string, string> }
export interface ProviderStatus { id: string; name: string; enabled: boolean; adapterAvailable: boolean; configured: boolean; mock: boolean; dataLocality?: 'LOCAL' | 'EXTERNAL' | 'UNKNOWN' }
export interface RuntimeConfig { mode: 'demo' | 'personal'; persistent: boolean; dataStorage: 'external'; cloudRequestsMayLeaveDevice: boolean }
export interface DataSharingConsent { providerId: string; taskContent: boolean; personalInstructions: boolean; knowledgeSourceIds: string[] }
export interface ProviderDataPermission { providerId: string; enabled: boolean; taskContent: boolean; personalInstructions: boolean; knowledgePassages: boolean }
export interface Conversation { id: string; agentId: string; messages: Message[]; updatedAt: string }
export interface ResolvedModelMetadata {
  providerId?: string;
  source?: string;
  execution?: ModelExecution | null;
  requestedModelId?: string | null;
  requestedTier?: string | null;
  resolvedModelId?: string;
  resolvedProviderId?: string;
  resolvedTier?: string | null;
  fallbackUsed?: boolean;
  fallbackReason?: string | null;
  estimatedCost?: number | null;
  currency?: string | null;
  inputTokens?: number | null;
  outputTokens?: number | null;
  usageId?: string;
}
export interface ModelExecution { providerId: string; modelId: string; apiModelId: string; source: string; fallbackUsed: boolean; mock: boolean; usageId: string }
export interface UsageRecord {
  id: string; providerId: string; modelId: string; agentId: string | null; workflow: string | null; conversationId: string | null;
  timestamp: string; inputTokens: number | null; outputTokens: number | null; cachedTokens: number | null;
  estimatedCost: number | null; currency: string | null; pricingUpdatedAt: string | null; status: string; mock: boolean; error: string | null;
}
export interface UsageSummary {
  agentId: string | null; modelId: string; providerId: string; requests: number; successfulRequests: number; failedRequests: number; mockRequests: number;
  inputTokens: number | null; outputTokens: number | null; estimatedCost: number | null; currency: string | null;
}
export interface ChatRequest extends ModelSelection { agentId: string; message: string }
export interface ChatResponse extends ResolvedModelMetadata { id: string; agentId: string; modelId: string; content: string; createdAt: string; conversationId?: string }
export interface Health { status: string; provider: string }
export interface Message extends ResolvedModelMetadata { id: string; role: 'user' | 'assistant'; content: string; createdAt: string; modelId?: string }
