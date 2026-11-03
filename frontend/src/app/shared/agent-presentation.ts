export function agentIcon(id: string): string {
  return ({ 'coding-agent': 'code', 'writing-agent': 'document', 'translation-agent': 'language', 'email-assistant': 'mail', 'documentation-agent': 'document' } as Record<string, string>)[id] ?? 'spark';
}
