import { inject, Injectable, signal } from '@angular/core';
import { HttpInterceptorFn } from '@angular/common/http';

@Injectable({ providedIn: 'root' })
export class RuntimeContextService {
  readonly mode = signal<'demo' | 'personal' | null>(null);
}

/** The server rejects stale-mode requests before reading private task content. */
export const runtimeModeInterceptor: HttpInterceptorFn = (request, next) => {
  const mode = inject(RuntimeContextService).mode();
  return next(mode ? request.clone({ setHeaders: { 'X-SAI-Mode': mode } }) : request);
};
