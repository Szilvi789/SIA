import { describe, expect, it } from 'vitest';
import { documentBlocks } from './document-formatting';
import { layoutPngPages, PNG_PAGE_SETTINGS, wrapText } from './png-layout';

const measure = (value: string) => Array.from(value).length * 12;

describe('PNG document pagination', () => {
  it('wraps at word boundaries and preserves the original Unicode and whitespace', () => {
    const text = 'Árvíztűrő  tükörfúrógép – Grüße aus Köln.';
    const lines = wrapText(text, 180, measure);

    expect(lines.length).toBeGreaterThan(1);
    expect(lines.join('')).toBe(text);
    expect(lines.every(line => measure(line) <= 180)).toBe(true);
    expect(lines.some(line => line.startsWith('tükörfúrógép'))).toBe(true);
  });

  it('splits a huge word without losing characters or cutting Unicode surrogate pairs', () => {
    const text = 'Á😀ő'.repeat(3000);
    const lines = wrapText(text, 240, measure);

    expect(lines.length).toBeGreaterThan(400);
    expect(lines.join('')).toBe(text);
    expect(lines.every(line => measure(line) <= 240)).toBe(true);
    expect(lines.every(line => !/[\uD800-\uDBFF]$/u.test(line))).toBe(true);
  });

  it('paginates explicit line breaks and blank paragraphs without truncating the last line', () => {
    const content = Array.from({ length: 200 }, (_, index) => index === 3 ? '' : `Line ${index}`).join('\n');
    const blocks = documentBlocks({ title: 'Document', content });
    const pages = layoutPngPages(blocks, measure);
    const lines = pages.flatMap(page => page.lines);

    expect(pages.length).toBeGreaterThan(3);
    expect(lines.map(line => line.text)).toEqual(blocks.map(block => block.text));
    expect(lines.at(-1)?.text).toBe('Line 199');
    expect(lines.every(line => line.y >= PNG_PAGE_SETTINGS.margin &&
      line.y < PNG_PAGE_SETTINGS.height - PNG_PAGE_SETTINGS.margin - PNG_PAGE_SETTINGS.footerHeight)).toBe(true);
  });

  it('paginates oversized words onto multiple bounded pages', () => {
    const content = 'é'.repeat(18000);
    const pages = layoutPngPages(documentBlocks({ title: '', content }), measure);

    expect(pages.length).toBeGreaterThan(3);
    expect(pages.flatMap(page => page.lines).map(line => line.text).join('')).toBe(content);
    expect(pages.every(page => page.lines.length < 50)).toBe(true);
  });

  it('reserves a blank line for an empty document and rejects unreadable page sizes', () => {
    const pages = layoutPngPages(documentBlocks({ title: '', content: '' }), measure);
    expect(pages).toHaveLength(1);
    expect(pages[0].lines[0].text).toBe('');
    expect(() => layoutPngPages([], measure, { width: 100, height: 100, margin: 60, footerHeight: 42 }))
      .toThrow('too small');
    expect(() => wrapText('text', 0, measure)).toThrow('positive');
  });
});
