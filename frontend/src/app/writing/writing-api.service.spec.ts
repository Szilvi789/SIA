import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { API_BASE_URL } from '../core/api.service';
import { WritingApiService } from './writing-api.service';
import { generatedResult, savedDraft, writingOptions } from './writing-test-data';

describe('WritingApiService', () => {
  let api: WritingApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), { provide: API_BASE_URL, useValue: '/custom-api' }] });
    api = TestBed.inject(WritingApiService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('sends generated export bytes to private server storage without a filesystem path', () => {
    api.storeExport(new Blob(['Synthetic exported text'], { type: 'text/plain' }), 'synthetic.txt', 'TXT').subscribe();
    const request = http.expectOne('/custom-api/writing/exports');
    expect(request.request.method).toBe('POST');
    expect(request.request.body instanceof FormData).toBe(true);
    expect(request.request.body.get('format')).toBe('TXT');
    expect(request.request.body.get('file').name).toBe('synthetic.txt');
    request.flush({ id: 'opaque-id', format: 'TXT' });
  });

  it('uses the configured API root for options and drafts', () => {
    api.options().subscribe(options => expect(options.capabilities.sending).toBe(false));
    http.expectOne('/custom-api/writing/options').flush(writingOptions());
    api.drafts().subscribe(drafts => expect(drafts[0].content).toBe('Saved manual text'));
    http.expectOne('/custom-api/writing/drafts').flush([savedDraft()]);
  });

  it('sends structured generation and instruction-preview requests without regenerating drafts', () => {
    const draft = savedDraft();
    api.generate(draft).subscribe(result => expect(result.mock).toBe(true));
    const generation = http.expectOne('/custom-api/writing/generate');
    expect(generation.request.method).toBe('POST');
    expect(generation.request.body.preferences.outputLanguage).toBe('HU');
    generation.flush(generatedResult());
    api.previewInstructions(draft).subscribe();
    const preview = http.expectOne('/custom-api/writing/preview-instructions');
    expect(preview.request.method).toBe('POST');
    preview.flush({ systemPrompt: 'Core instructions', appliedRuleIds: [] });
  });

  it('creates new drafts and updates existing drafts with their optimistic revision', () => {
    api.saveDraft({ ...savedDraft(), id: '' }).subscribe();
    const create = http.expectOne('/custom-api/writing/drafts');
    expect(create.request.method).toBe('POST');
    create.flush(savedDraft());
    api.saveDraft({ ...savedDraft(), id: 'draft/one', revision: 4 }).subscribe();
    const update = http.expectOne('/custom-api/writing/drafts/draft%2Fone');
    expect(update.request.method).toBe('PUT');
    expect(update.request.body.revision).toBe(4);
    update.flush(savedDraft());
  });
});
