import { bootstrapApplication } from '@angular/platform-browser';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withHashLocation } from '@angular/router';
import { AppComponent } from './app/app.component';
import { API_BASE_URL } from './app/core/api.service';
import { routes } from './app/app.routes';
import { runtimeModeInterceptor } from './app/core/runtime-context.service';

async function start(): Promise<void> {
  const response = await fetch('./config.json');
  if (!response.ok) throw new Error('Unable to load application configuration.');
  const config: unknown = await response.json();
  if (!config || typeof config !== 'object' || !('apiBaseUrl' in config) || typeof config.apiBaseUrl !== 'string') {
    throw new Error('config.json must contain an apiBaseUrl string.');
  }
  await bootstrapApplication(AppComponent, {
    providers: [
      provideHttpClient(withInterceptors([runtimeModeInterceptor])),
      provideRouter(routes, withHashLocation()),
      { provide: API_BASE_URL, useValue: config.apiBaseUrl.replace(/\/$/, '') }
    ]
  });
}

start().catch((error: unknown) => {
  console.error(error);
  const message = document.createElement('p');
  message.textContent = 'Agent Studio could not start. Check config.json and reload the application.';
  message.style.cssText = 'padding:3rem;font-family:system-ui;color:#713a30';
  document.body.replaceChildren(message);
});
