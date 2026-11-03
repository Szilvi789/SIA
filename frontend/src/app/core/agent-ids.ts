export const WRITING_AGENT_ID = 'writing-agent';
export const LEGACY_WRITING_AGENT_IDS = ['translation-agent', 'email-assistant'];

export function canonicalAgentId(id: string): string {
  return LEGACY_WRITING_AGENT_IDS.includes(id) ? WRITING_AGENT_ID : id;
}
