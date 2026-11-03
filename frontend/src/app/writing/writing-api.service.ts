import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../core/api.service';
import { InstructionPreview, WritingDraft, WritingOptions, WritingPreset, WritingRequest, WritingResult } from './writing.models';

@Injectable({ providedIn: 'root' })
export class WritingApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${inject(API_BASE_URL)}/writing`;

  options(): Observable<WritingOptions> { return this.http.get<WritingOptions>(`${this.baseUrl}/options`); }
  generate(request: WritingRequest): Observable<WritingResult> { return this.http.post<WritingResult>(`${this.baseUrl}/generate`, request); }
  drafts(): Observable<WritingDraft[]> { return this.http.get<WritingDraft[]>(`${this.baseUrl}/drafts`); }
  saveDraft(draft: WritingDraft): Observable<WritingDraft> {
    return draft.id
      ? this.http.put<WritingDraft>(`${this.baseUrl}/drafts/${encodeURIComponent(draft.id)}`, draft)
      : this.http.post<WritingDraft>(`${this.baseUrl}/drafts`, draft);
  }
  savePreset(preset: WritingPreset, existing = true): Observable<WritingPreset> {
    return existing
      ? this.http.put<WritingPreset>(`${this.baseUrl}/presets/${encodeURIComponent(preset.id)}`, preset)
      : this.http.post<WritingPreset>(`${this.baseUrl}/presets`, preset);
  }
  previewInstructions(request: WritingRequest): Observable<InstructionPreview> {
    return this.http.post<InstructionPreview>(`${this.baseUrl}/preview-instructions`, request);
  }
}
