import { Injectable } from '@angular/core';
import type { Content, TDocumentDefinitions } from 'pdfmake/interfaces';
import { documentBlocks, sanitizeFilename } from './document-formatting';
import { DocumentExportFormat, ExportArtifact, ExportDocument } from './export.models';
import { layoutPngPages, PNG_PAGE_SETTINGS } from './png-layout';

/** No HTML parsing, remote images, network resources or AI-provider calls are used. */
export function pdfDefinition(document: ExportDocument): TDocumentDefinitions {
  const content: Content[] = documentBlocks(document).map(block => {
    switch (block.kind) {
      case 'title': return { text: block.text, style: 'title' };
      case 'subject': return { text: block.text, style: 'subject' };
      case 'heading': return { text: block.text, style: `heading${block.level ?? 1}` };
      case 'bullet': return { text: `• ${block.text}`, margin: [12, 0, 0, 5] };
      default: return { text: block.text || ' ', margin: [0, 0, 0, 5], preserveLeadingSpaces: true };
    }
  });
  return {
    pageSize: 'A4', pageMargins: [48, 48, 48, 48],
    info: { title: document.title || 'SAI document', creator: 'SAI Writing & Communication' },
    defaultStyle: { font: 'Roboto', fontSize: 11, lineHeight: 1.25 },
    styles: {
      title: { fontSize: 22, bold: true, margin: [0, 0, 0, 16] },
      subject: { fontSize: 12, bold: true, margin: [0, 0, 0, 12] },
      heading1: { fontSize: 17, bold: true, margin: [0, 10, 0, 7] },
      heading2: { fontSize: 14, bold: true, margin: [0, 8, 0, 6] },
      heading3: { fontSize: 12, bold: true, margin: [0, 7, 0, 5] }
    },
    content
  };
}

@Injectable({ providedIn: 'root' })
export class DocumentExportService {
  /** Public for callers that also name attachments or draft downloads. */
  sanitizeFilename(title: string): string {
    return sanitizeFilename(title);
  }

  async export(format: DocumentExportFormat, document: ExportDocument): Promise<ExportArtifact[]> {
    // Capture the latest editor values before loading a converter asynchronously.
    const snapshot: ExportDocument = { title: document.title, subject: document.subject, content: document.content };
    const filename = sanitizeFilename(snapshot.title);
    switch (format) {
      case 'TXT': {
        const text = [snapshot.title, snapshot.subject ? `Subject: ${snapshot.subject}` : undefined]
          .filter(value => value !== undefined && value !== '').join('\n');
        const prefix = text ? `${text}\n\n` : '';
        return [{ filename: `${filename}.txt`, blob: new Blob([prefix, snapshot.content], { type: 'text/plain;charset=utf-8' }) }];
      }
      case 'PDF': return [{ filename: `${filename}.pdf`, blob: await this.pdf(snapshot) }];
      case 'DOCX': return [{ filename: `${filename}.docx`, blob: await this.docx(snapshot) }];
      case 'PNG': return this.png(snapshot, filename);
      default: throw new Error('This export format is not supported.');
    }
  }

  private async pdf(document: ExportDocument): Promise<Blob> {
    // Browser build and embedded Roboto fonts are intentionally separate lazy chunks.
    const [library, fonts] = await Promise.all([
      import('pdfmake/build/pdfmake'), import('pdfmake/build/vfs_fonts')
    ]);
    const pdfMake = library.default ?? library;
    const vfs = fonts.default ?? fonts;
    const generator = pdfMake.createPdf(pdfDefinition(document), undefined, {
      Roboto: {
        normal: 'Roboto-Regular.ttf', bold: 'Roboto-Medium.ttf',
        italics: 'Roboto-Italic.ttf', bolditalics: 'Roboto-MediumItalic.ttf'
      }
    }, vfs);
    // All fonts are local VFS entries, so the synchronous stream needs no URL resolver.
    // Unlike callback-only getBlob, stream errors can be reported to the workspace.
    return new Promise<Blob>((resolve, reject) => {
      const chunks: Uint8Array<ArrayBuffer>[] = [];
      const stream = generator.getStream();
      stream.on('data', (chunk: Uint8Array) => chunks.push(new Uint8Array(chunk)));
      stream.on('error', reject);
      stream.on('end', () => resolve(new Blob(chunks, { type: 'application/pdf' })));
      stream.end();
    });
  }

  private async docx(document: ExportDocument): Promise<Blob> {
    const { Document, Paragraph, TextRun, HeadingLevel, Packer } = await import('docx');
    const children = documentBlocks(document).map(block => new Paragraph({
      children: [new TextRun({ text: block.text, bold: block.kind === 'subject' })],
      ...(block.kind === 'title' ? { heading: HeadingLevel.TITLE } : {}),
      ...(block.kind === 'heading' ? { heading: block.level === 1 ? HeadingLevel.HEADING_1
        : block.level === 2 ? HeadingLevel.HEADING_2 : HeadingLevel.HEADING_3 } : {}),
      ...(block.kind === 'bullet' ? { bullet: { level: 0 } } : {}),
      spacing: { after: block.kind === 'title' ? 240 : 120 }
    }));
    return Packer.toBlob(new Document({
      title: document.title, creator: 'SAI Writing & Communication',
      styles: { default: { document: { run: { font: 'Calibri', size: 22 } } } },
      sections: [{ children }]
    }));
  }

  private async png(document: ExportDocument, filename: string): Promise<ExportArtifact[]> {
    const canvas = globalThis.document.createElement('canvas');
    const context = canvas.getContext('2d');
    if (!context) throw new Error('PNG export requires a browser with Canvas 2D support.');
    const pages = layoutPngPages(documentBlocks(document), (text, font) => {
      context.font = font;
      return context.measureText(text).width;
    });
    const artifacts: ExportArtifact[] = [];
    for (const [index, page] of pages.entries()) {
      canvas.width = PNG_PAGE_SETTINGS.width;
      canvas.height = PNG_PAGE_SETTINGS.height;
      context.fillStyle = '#ffffff';
      context.fillRect(0, 0, canvas.width, canvas.height);
      context.fillStyle = '#18251e';
      context.textBaseline = 'top';
      for (const line of page.lines) {
        context.font = line.font;
        context.fillText(line.text, line.x, line.y);
      }
      context.font = '18px system-ui, sans-serif';
      context.fillStyle = '#59645d';
      context.fillText(`${index + 1} / ${pages.length}`, PNG_PAGE_SETTINGS.margin,
        canvas.height - PNG_PAGE_SETTINGS.margin);
      const blob = await new Promise<Blob>((resolve, reject) => canvas.toBlob(value => {
        if (value) resolve(value);
        else reject(new Error('The browser could not create a PNG image.'));
      }, 'image/png'));
      const suffix = pages.length > 1 ? `-page-${String(index + 1).padStart(2, '0')}` : '';
      artifacts.push({ filename: `${filename}${suffix}.png`, blob });
    }
    return artifacts;
  }
}
