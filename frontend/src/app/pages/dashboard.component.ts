import { Component, computed, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { WorkspaceService } from '../core/workspace.service';
import { IconComponent } from '../shared/icon.component';
import { agentIcon } from '../shared/agent-presentation';
import { canonicalAgentId } from '../core/agent-ids';

@Component({
  standalone: true,
  imports: [RouterLink, MatButtonModule, IconComponent],
  template: `
    <div class="page-title"><div><div class="eyebrow intro-label">A LITTLE FOCUS. A LOT OF POSSIBILITY.</div><h1>Your ideas, in good company.</h1><p>A thoughtful workspace for the things you want to make happen.</p></div><span class="today">{{today}}</span></div>
    <section class="welcome-banner" aria-labelledby="welcome-title"><div class="welcome-copy"><span class="hero-label"><span></span> YOUR PERSONAL AGENT WORKSPACE</span><h2 id="welcome-title">Less busywork.<br>More room to create.</h2><p>Write, build, translate, and explore with a small team<br class="desktop-break"> of agents shaped around the way you work.</p><a mat-flat-button routerLink="/chat" class="button-icon">Start a conversation <app-icon name="arrow" /></a><span class="hero-footnote">Local workspace · Mock AI responses</span></div><div class="hero-art" aria-hidden="true"><div class="orbit orbit-one"></div><div class="orbit orbit-two"></div><div class="orbit orbit-three"></div><div class="central-star"><app-icon name="spark" /></div><span class="orbit-item orbit-code"><app-icon name="code" /></span><span class="orbit-item orbit-language"><app-icon name="language" /></span><span class="orbit-item orbit-mail"><app-icon name="mail" /></span><span class="art-dot dot-one"></span><span class="art-dot dot-two"></span><span class="art-dot dot-three"></span></div></section>
    <section class="workspace-stats" aria-label="Workspace overview"><div><span class="stat-icon"><app-icon name="agents" /></span><span><strong>{{workspace.agents().length}}</strong><small>Ready-to-use agents</small></span><span class="stat-detail">Your personal team</span></div><div><span class="stat-icon"><app-icon name="chat" /></span><span><strong>{{conversationCount()}}</strong><small>Conversations</small></span><span class="stat-detail">This browser session</span></div><div><span class="stat-icon"><app-icon name="shield" /></span><span><strong class="stat-word">Local</strong><small>Development workspace</small></span><span class="stat-detail">No API keys needed</span></div></section>
    <div class="section-heading"><div><h2>Meet your agents</h2><p class="section-subtitle">A starting point for whatever is on your mind.</p></div><a routerLink="/agents">Manage agents <app-icon name="arrow" /></a></div>
    <section class="agent-grid" aria-label="Available agents">
      @if (workspace.loading()) {<div class="empty-loading panel">Loading your agents…</div>}
      @for (agent of workspace.agents(); track agent.id) {
        <article class="agent-card panel"><div class="card-top"><span [class]="'agent-icon ' + agent.id"><app-icon [name]="iconFor(agent.id)" /></span><span class="agent-ready"><span></span> Ready</span></div><h3>{{agent.name}}</h3><p>{{agent.description}}</p><div class="card-bottom"><span class="tag">Mock provider</span><button mat-icon-button [attr.aria-label]="'Chat with ' + agent.name" (click)="openAgent(agent.id)"><app-icon name="arrow" /></button></div></article>
      }
    </section>
    <section class="getting-started panel"><div class="getting-started-heading"><span class="small-spark"><app-icon name="bolt" /></span><div><h2>Make this workspace yours</h2><p>A few small steps to find your flow.</p></div><span class="phase-label">FOUNDATION · PHASE 01</span></div><div class="steps"><a routerLink="/agents"><span class="step-number">01</span><div><strong>Choose your agent</strong><p>Start with a preset, then make it your own.</p></div><app-icon name="arrow" /></a><a routerLink="/chat"><span class="step-number">02</span><div><strong>Try a conversation</strong><p>Explore the workflow with mock responses.</p></div><app-icon name="arrow" /></a><a routerLink="/settings"><span class="step-number">03</span><div><strong>Know your workspace</strong><p>See what is connected and what comes next.</p></div><app-icon name="arrow" /></a></div></section>
    <footer class="workspace-footer"><span>Built for a calmer kind of productivity.</span><span><i></i> All systems local</span></footer>
  `,
  styleUrl: './dashboard.component.scss'
})
export class DashboardComponent {
  readonly workspace = inject(WorkspaceService);
  private readonly router = inject(Router);
  readonly iconFor = agentIcon;
  readonly today = new Intl.DateTimeFormat('en', { weekday: 'short', month: 'short', day: 'numeric' }).format(new Date());
  readonly conversationCount = computed(() => new Set(Object.entries(this.workspace.conversations())
    .filter(([, messages]) => messages.length > 0).map(([id]) => canonicalAgentId(id))).size);
  openAgent(id: string): void {
    this.workspace.selectAgent(id);
    void this.router.navigate([id === 'writing-agent' ? '/writing' : '/chat']);
  }
}
