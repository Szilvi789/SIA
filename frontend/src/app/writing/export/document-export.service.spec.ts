import JSZip from 'jszip';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { DocumentExportService, pdfDefinition } from './document-export.service';
import { documentBlocks, sanitizeFilename } from './document-formatting';
import { ExportDocument } from './export.models';

function readBlob(blob: Blob): Promise<ArrayBuffer> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () => reject(reader.error);
    reader.onload = () => resolve(reader.result as ArrayBuffer);
    reader.readAsArrayBuffer(blob);
  });
}

const editedDocument: ExportDocument = {
  title: 'Bewerbung – Árvíz', subject: 'Aktualisierter Betreff',
  content: '## Edited heading\nÁrvíztűrő tükörfúrógép – Grüße aus Köln.\n\n- My edited achievement\n<b>Plain text & no HTML</b>'
};

describe('DocumentExportService', () => {
  const service = new DocumentExportService();

  afterEach(() => vi.restoreAllMocks());

  it('exports the exact edited source as UTF-8 text, including blank lines and markup', async () => {
    const [artifact] = await service.export('TXT', editedDocument);
    const text = new TextDecoder().decode(await readBlob(artifact.blob));

    expect(artifact.filename).toBe('Bewerbung – Árvíz.txt');
    expect(artifact.blob.type).toBe('text/plain;charset=utf-8');
    expect(text).toBe(`${editedDocument.title}\nSubject: ${editedDocument.subject}\n\n${editedDocument.content}`);
  });

  it('creates a real PDF with embedded Unicode fonts using the edited snapshot', async () => {
    const library = await import('pdfmake/build/pdfmake');
    const create = vi.spyOn(library.default ?? library, 'createPdf');
    const [artifact] = await service.export('PDF', editedDocument);
    const bytes = new Uint8Array(await readBlob(artifact.blob));
    const pdf = new TextDecoder('latin1').decode(bytes);

    expect(artifact.filename).toBe('Bewerbung – Árvíz.pdf');
    expect(artifact.blob.type).toBe('application/pdf');
    expect(pdf.startsWith('%PDF-')).toBe(true);
    expect(pdf).toContain('/FontFile2');
    expect(pdf).toContain('/ToUnicode');
    expect(create).toHaveBeenCalledWith(expect.objectContaining({
      content: expect.arrayContaining([
        expect.objectContaining({ text: 'Árvíztűrő tükörfúrógép – Grüße aus Köln.' }),
        expect.objectContaining({ text: '<b>Plain text & no HTML</b>' })
      ])
    }), undefined, expect.objectContaining({ Roboto: expect.any(Object) }),
    expect.objectContaining({ 'Roboto-Regular.ttf': expect.any(String) }));
  }, 15000);

  it('creates a real DOCX archive with edited Unicode text, headings and bullets', async () => {
    const [artifact] = await service.export('DOCX', editedDocument);
    const buffer = await readBlob(artifact.blob);
    expect(Array.from(new Uint8Array(buffer).slice(0, 4))).toEqual([0x50, 0x4b, 0x03, 0x04]);
    expect(artifact.filename).toBe('Bewerbung – Árvíz.docx');
    expect(artifact.blob.type).toBe('application/vnd.openxmlformats-officedocument.wordprocessingml.document');
    const zip = await JSZip.loadAsync(buffer);
    const xml = await zip.file('word/document.xml')!.async('string');
    const parsed = new DOMParser().parseFromString(xml, 'application/xml');
    const text = Array.from(parsed.getElementsByTagName('w:t')).map(node => node.textContent).join('\n');

    expect(text).toContain('Bewerbung – Árvíz');
    expect(text).toContain('Subject: Aktualisierter Betreff');
    expect(text).toContain('Árvíztűrő tükörfúrógép – Grüße aus Köln.');
    expect(text).toContain('<b>Plain text & no HTML</b>');
    expect(xml).toContain('w:val="Heading2"');
    expect(xml).toContain('<w:numPr>');
    expect(zip.file('word/vbaProject.bin')).toBeNull();
  }, 15000);

  it('captures editor values before a lazy converter loads', async () => {
    const mutable = { title: 'Latest title', subject: 'Latest subject', content: 'Latest manually edited body' };
    const result = service.export('DOCX', mutable);
    mutable.content = 'An edit made after export was requested';
    const [artifact] = await result;
    const archive = await JSZip.loadAsync(await readBlob(artifact.blob));
    const xml = await archive.file('word/document.xml')!.async('string');

    expect(xml).toContain('Latest manually edited body');
    expect(xml).not.toContain('An edit made after export was requested');
  });

  it('renders every long-document page through Canvas and names each PNG distinctly', async () => {
    const fillText = vi.fn();
    const context = {
      font: '', fillStyle: '', textBaseline: '', fillText, fillRect: vi.fn(),
      measureText: (value: string) => ({ width: Array.from(value).length * 12 })
    } as unknown as CanvasRenderingContext2D;
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(context);
    const toBlob = vi.spyOn(HTMLCanvasElement.prototype, 'toBlob').mockImplementation(callback => {
      callback(new Blob([new Uint8Array([137, 80, 78, 71, 13, 10, 26, 10])], { type: 'image/png' }));
    });
    const content = Array.from({ length: 125 }, (_, index) => `Edited line ${index} – Árvíz`).join('\n');
    const artifacts = await service.export('PNG', { title: 'Edited document', content });

    expect(artifacts.length).toBeGreaterThan(1);
    expect(artifacts[0].filename).toBe('Edited document-page-01.png');
    expect(new Set(artifacts.map(artifact => artifact.filename)).size).toBe(artifacts.length);
    expect(toBlob).toHaveBeenCalledTimes(artifacts.length);
    expect(toBlob.mock.calls.every(call => call[1] === 'image/png')).toBe(true);
    expect(fillText).toHaveBeenCalledWith('Edited line 124 – Árvíz', expect.any(Number), expect.any(Number));
    expect(artifacts.every(artifact => artifact.blob.type === 'image/png')).toBe(true);
  });

  it('reports unsupported Canvas and failed encoding instead of producing a fake image', async () => {
    const getContext = vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(null);
    await expect(service.export('PNG', editedDocument)).rejects.toThrow('Canvas 2D support');
    getContext.mockReturnValue({
      measureText: () => ({ width: 30 }), fillRect: vi.fn(), fillText: vi.fn()
    } as unknown as CanvasRenderingContext2D);
    vi.spyOn(HTMLCanvasElement.prototype, 'toBlob').mockImplementation(callback => callback(null));
    await expect(service.export('PNG', editedDocument)).rejects.toThrow('could not create a PNG');
  });
});

describe('export document formatting', () => {
  it('preserves Unicode filenames while preventing paths, control characters and reserved names', () => {
    expect(sanitizeFilename('Lebenslauf – Árvíztűrő')).toBe('Lebenslauf – Árvíztűrő');
    const name = sanitizeFilename('../../Bewerbung\\Árvíz:\u0000?*\u202e.docx');
    expect(name).not.toMatch(/[\\/:*?"<>|\u0000\u202e]/u);
    expect(name).not.toContain('..');
    expect(name.startsWith('.')).toBe(false);
    expect(sanitizeFilename('CON')).toBe('SAI-CON');
    expect(sanitizeFilename('...')).toBe('sai-document');
    expect(Array.from(sanitizeFilename('Á'.repeat(200)))).toHaveLength(100);
  });

  it('recognizes only simple headings and lists while leaving HTML and links as plain text', () => {
    const blocks = documentBlocks(editedDocument);
    expect(blocks).toContainEqual({ kind: 'heading', text: 'Edited heading', level: 2 });
    expect(blocks).toContainEqual({ kind: 'bullet', text: 'My edited achievement' });
    expect(blocks).toContainEqual({ kind: 'body', text: '<b>Plain text & no HTML</b>' });
    expect(JSON.stringify(pdfDefinition({ title: '', content: 'https://example.invalid/image.png' })))
      .not.toMatch(/"(?:image|link|html)":/u);
  });
});
