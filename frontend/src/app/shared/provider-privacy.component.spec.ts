import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { ProviderPrivacyComponent } from './provider-privacy.component';

describe('ProviderPrivacyComponent', () => {
  let http: HttpTestingController;
  const denied = { providerId: 'openai', enabled: false, taskContent: false, personalInstructions: false, knowledgePassages: false };
  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [ProviderPrivacyComponent], providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('disables permission changes and explains cloud sharing in demo mode', () => {
    const fixture = TestBed.createComponent(ProviderPrivacyComponent);
    http.expectOne('/api/provider-data-permissions').flush([denied]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Demo mode blocks cloud requests');
    expect(fixture.nativeElement.querySelector('fieldset').disabled).toBe(true);
    fixture.componentInstance.save({ ...denied, enabled: true });
    http.expectNone('/api/providers/openai/data-permission');
  });

  it('saves distinct personal provider permissions without a credential field', () => {
    const fixture = TestBed.createComponent(ProviderPrivacyComponent);
    fixture.componentRef.setInput('personal', true);
    http.expectOne('/api/provider-data-permissions').flush([denied]);
    fixture.componentInstance.save({ ...denied, enabled: true, taskContent: true, personalInstructions: true });
    const request = http.expectOne('/api/providers/openai/data-permission');
    expect(request.request.body).toEqual({ ...denied, enabled: true, taskContent: true, personalInstructions: true });
    request.flush(request.request.body);
    expect(fixture.componentInstance.status()).toContain('private database');
  });
});
