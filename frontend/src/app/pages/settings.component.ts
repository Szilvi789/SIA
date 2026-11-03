import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { ApiService } from '../core/api.service';
import { WorkspaceService } from '../core/workspace.service';
import { IconComponent } from '../shared/icon.component';

@Component({
  standalone: true,
  imports: [RouterLink, MatButtonModule, IconComponent],
  template: `
    <div class="page-title"><div><div class="eyebrow intro-label">THE DETAILS BEHIND YOUR WORKSPACE</div><h1>A little clarity, by design.</h1><p>See what is connected, where things live, and what this release can do.</p></div></div>
    <div class="settings-grid"><section class="panel connection-panel"><div class="panel-heading"><span class="setting-icon"><app-icon name="globe" /></span><div><h2>Your connection</h2><p>A local backend powers this workspace.</p></div><span class="status-pill" [class.offline]="!workspace.online()"><span></span>{{workspace.loading() ? 'Connecting' : workspace.online() ? 'Connected' : 'Offline'}}</span></div><dl><div><dt>API base URL</dt><dd><code>{{api.baseUrl}}</code></dd></div><div><dt>Provider</dt><dd>Mock AI provider <span class="inline-label">ACTIVE IN THIS RELEASE</span></dd></div><div><dt>Available models</dt><dd>@if(workspace.models().length){@for(model of workspace.models(); track model.id){<span class="model-name">{{model.name}}</span>}}@else{Waiting for the backend}</dd></div><div><dt>Health status</dt><dd>{{workspace.online() ? 'UP · Local service is responding' : 'No active connection'}}</dd></div></dl><div class="connection-actions"><span>Connection settings use the application runtime configuration.</span><button mat-stroked-button (click)="workspace.load()" [disabled]="workspace.loading()">{{workspace.loading() ? 'Checking…' : 'Check connection'}}</button></div></section>
    <aside class="release-card"><span class="eyebrow">PERSONAL AI AGENT PLATFORM</span><span class="release-icon"><app-icon name="spark" /></span><h2>A foundation<br>for your next idea.</h2><p>A small, working beginning with room to grow. This workspace uses local demo responses to bring the experience to life.</p><span class="release-badge">PHASE 01 · FOUNDATION</span></aside></div>
    <section class="panel session-panel"><div class="panel-heading"><span class="setting-icon"><app-icon name="shield" /></span><div><h2>A few things to know</h2><p>Simple boundaries for this first chapter.</p></div></div><div class="information-grid"><div><span class="info-number">01</span><h3>Your conversations are temporary.</h3><p>Message history stays in this browser session while you navigate. Refreshing or closing the application clears it. Messages are sent individually; the mock provider does not use previous turns.</p></div><div><span class="info-number">02</span><h3>Your agent settings live in memory.</h3><p>Saved configurations stay on the backend until it restarts. Permanent storage and custom agent creation are planned for a later release.</p></div><div><span class="info-number">03</span><h3>This is a mock AI workspace.</h3><p>No external AI service is connected. The provider returns demonstration text. Tools, filesystem access, and terminal execution are unavailable.</p></div></div></section>
    <div class="settings-footer"><span>Want to change how your agents respond?</span><a routerLink="/agents">Configure your agents <app-icon name="arrow" /></a></div>
  `,
  styleUrl: './settings.component.scss'
})
export class SettingsComponent {
  readonly api = inject(ApiService);
  readonly workspace = inject(WorkspaceService);
}
