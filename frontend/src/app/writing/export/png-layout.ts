import { DocumentBlock } from './document-formatting';

export interface PngPageSettings {
  readonly width: number;
  readonly height: number;
  readonly margin: number;
  readonly footerHeight: number;
}

export const PNG_PAGE_SETTINGS: PngPageSettings = {
  width: 1240, height: 1754, margin: 72, footerHeight: 42
};

export interface PngTextLine {
  readonly text: string;
  readonly x: number;
  readonly y: number;
  readonly font: string;
}

export interface PngPage {
  readonly lines: readonly PngTextLine[];
}

export type MeasureText = (text: string, font: string) => number;

/** Preserve whitespace and Unicode while wrapping words and oversized individual tokens. */
export function wrapText(text: string, maxWidth: number, measure: (text: string) => number): string[] {
  if (!(maxWidth > 0)) throw new Error('The text width must be positive.');
  if (!text) return [''];
  const lines: string[] = [];
  let line = '';
  for (const token of text.match(/\s+|\S+/gu) ?? []) {
    if (measure(line + token) <= maxWidth) {
      line += token;
      continue;
    }
    if (measure(token) <= maxWidth) {
      if (line) lines.push(line);
      line = token;
      continue;
    }
    // Array.from iterates code points instead of splitting UTF-16 surrogate pairs.
    for (const character of Array.from(token)) {
      if (line && measure(line + character) > maxWidth) {
        lines.push(line);
        line = '';
      }
      line += character;
    }
  }
  if (line) lines.push(line);
  return lines;
}

function blockStyle(block: DocumentBlock): { font: string; lineHeight: number; after: number } {
  switch (block.kind) {
    case 'title': return { font: '600 42px system-ui, sans-serif', lineHeight: 58, after: 20 };
    case 'subject': return { font: '600 26px system-ui, sans-serif', lineHeight: 39, after: 18 };
    case 'heading': return {
      font: `600 ${block.level === 1 ? 34 : 29}px system-ui, sans-serif`, lineHeight: 44, after: 10
    };
    default: return { font: '24px system-ui, sans-serif', lineHeight: 36, after: 3 };
  }
}

/** Paginate before allocating images so long documents never exceed canvas height limits. */
export function layoutPngPages(
  blocks: readonly DocumentBlock[], measure: MeasureText, settings = PNG_PAGE_SETTINGS
): PngPage[] {
  const bottom = settings.height - settings.margin - settings.footerHeight;
  const availableWidth = settings.width - 2 * settings.margin;
  if (availableWidth <= 0 || bottom - settings.margin < 58) {
    throw new Error('The PNG page is too small for readable text.');
  }
  const pages: Array<{ lines: PngTextLine[] }> = [{ lines: [] }];
  let y = settings.margin;
  for (const block of blocks) {
    const style = blockStyle(block);
    const text = block.kind === 'bullet' ? `• ${block.text}` : block.text;
    for (const line of wrapText(text, availableWidth, value => measure(value, style.font))) {
      if (y + style.lineHeight > bottom) {
        pages.push({ lines: [] });
        y = settings.margin;
      }
      pages[pages.length - 1].lines.push({ text: line, x: settings.margin, y, font: style.font });
      y += style.lineHeight;
    }
    y += style.after;
  }
  return pages;
}
