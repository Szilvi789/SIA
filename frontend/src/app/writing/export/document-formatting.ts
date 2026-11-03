import { ExportDocument } from './export.models';

export interface DocumentBlock {
  readonly kind: 'title' | 'subject' | 'heading' | 'body' | 'bullet';
  readonly text: string;
  readonly level?: number;
}

/** A deliberately small Markdown subset, with all other input kept as plain text. */
export function documentBlocks(document: ExportDocument): DocumentBlock[] {
  const blocks: DocumentBlock[] = [];
  if (document.title) blocks.push({ kind: 'title', text: document.title });
  if (document.subject) blocks.push({ kind: 'subject', text: `Subject: ${document.subject}` });
  for (const line of document.content.replace(/\r\n?/gu, '\n').split('\n')) {
    const heading = /^(#{1,3})\s+(.+)$/u.exec(line);
    const bullet = /^\s*[-*+]\s+(.+)$/u.exec(line);
    if (heading) {
      blocks.push({ kind: 'heading', text: heading[2], level: heading[1].length });
    } else if (bullet) {
      blocks.push({ kind: 'bullet', text: bullet[1] });
    } else {
      blocks.push({ kind: 'body', text: line });
    }
  }
  return blocks;
}

/** Keep Unicode names while removing paths, control characters and reserved device names. */
export function sanitizeFilename(title: string): string {
  let name = title.normalize('NFC')
    .replace(/[\p{Cc}\p{Cf}]/gu, '')
    .replace(/[\\/:*?"<>|]/gu, '-')
    .replace(/\.{2,}/gu, '-')
    .replace(/\s+/gu, ' ')
    .replace(/^[ .-]+|[ .-]+$/gu, '')
    .replace(/\.(?:txt|pdf|docx|png)$/iu, '');
  name = Array.from(name).slice(0, 100).join('').replace(/[ .-]+$/gu, '');
  if (!name) return 'sai-document';
  if (/^(?:con|prn|aux|nul|com[1-9]|lpt[1-9])(?:\.|$)/iu.test(name)) name = `SAI-${name}`;
  return name;
}
