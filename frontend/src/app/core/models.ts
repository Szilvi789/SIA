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
}

export type AgentUpdate = Omit<Agent, 'id'>;

export interface AIModel {
  id: string;
  name: string;
  providerId: string;
  description: string;
}

export interface ChatRequest { agentId: string; modelId: string; message: string }
export interface ChatResponse { id: string; agentId: string; modelId: string; content: string; createdAt: string }
export interface Health { status: string; provider: string }
export interface Message { id: string; role: 'user' | 'assistant'; content: string; createdAt: string; modelId?: string }
