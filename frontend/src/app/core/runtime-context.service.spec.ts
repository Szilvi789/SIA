import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { RuntimeContextService, runtimeModeInterceptor } from './runtime-context.service';

describe('Runtime mode request binding', () => {
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(withInterceptors([runtimeModeInterceptor])), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('binds private requests to the discovered mode without putting content into the URL', () => {
    TestBed.inject(RuntimeContextService).mode.set('personal');
    TestBed.inject(HttpClient).post('/api/chat', { message: 'Synthetic task' }).subscribe();
    const request = http.expectOne('/api/chat');
    expect(request.request.headers.get('X-SAI-Mode')).toBe('personal');
    expect(request.request.urlWithParams).toBe('/api/chat');
    request.flush({});
  });
  it('allows initial mode discovery before a session is bound', () => {
    TestBed.inject(HttpClient).get('/api/runtime-config').subscribe();
    const request = http.expectOne('/api/runtime-config');
    expect(request.request.headers.has('X-SAI-Mode')).toBe(false);
    request.flush({ mode: 'demo' });
  });
});
