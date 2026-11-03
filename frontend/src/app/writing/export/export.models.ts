/** The export boundary accepts a snapshot of the editor, never a generation request. */
export interface ExportDocument {
  readonly title: string;
  readonly subject?: string;
  readonly content: string;
}

export type DocumentExportFormat = 'TXT' | 'PDF' | 'DOCX' | 'PNG';

/** PNG can produce multiple page artifacts; the caller controls downloads/attachments. */
export interface ExportArtifact {
  readonly filename: string;
  readonly blob: Blob;
}
