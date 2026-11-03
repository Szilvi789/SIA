import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable, InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';
import { Agent, AgentUpdate, AIModel, ChatRequest, ChatResponse, Health } from './models';

export const API_BASE_URL = new InjectionToken<string>('API_BASE_URL', { factory: () => '/api' });

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  readonly baseUrl = inject(API_BASE_URL);

  health(): Observable<Health> { return this.http.get<Health>(`${this.baseUrl}/health`); }
  agents(): Observable<Agent[]> { return this.http.get<Agent[]>(`${this.baseUrl}/agents`); }
  agent(id: string): Observable<Agent> { return this.http.get<Agent>(`${this.baseUrl}/agents/${encodeURIComponent(id)}`); }
  updateAgent(id: string, agent: AgentUpdate): Observable<Agent> {
    return this.http.put<Agent>(`${this.baseUrl}/agents/${encodeURIComponent(id)}`, agent);
  }
  models(): Observable<AIModel[]> { return this.http.get<AIModel[]>(`${this.baseUrl}/models`); }
  chat(request: ChatRequest): Observable<ChatResponse> { return this.http.post<ChatResponse>(`${this.baseUrl}/chat`, request); }
}

export function errorMessage(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) return 'The local backend is unavailable. Start it on port 8080, then try again.';
    if (error.error && typeof error.error.detail === 'string') return error.error.detail;
    if (error.error && typeof error.error.message === 'string') return error.error.message;
    return `The request could not be completed (HTTP ${error.status}). Please try again.`;
  }
  return 'Something went wrong. Please try again.';
}
