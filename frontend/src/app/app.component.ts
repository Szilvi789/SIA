import { Component, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { IconComponent } from './shared/icon.component';
import { WorkspaceService } from './core/workspace.service';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, RouterOutlet, MatButtonModule, IconComponent],
  template: `
    <a class="skip-link" href="#main-content" (click)="$event.preventDefault(); mainContent.focus()">Skip to content</a>
    <div class="app-shell">
      @if (menuOpen()) { <button class="sidebar-overlay" aria-label="Close navigation" (click)="menuOpen.set(false)"></button> }
      <aside class="sidebar" [class.is-open]="menuOpen()" aria-label="Main navigation">
        <a routerLink="/dashboard" class="brand" (click)="menuOpen.set(false)"><span class="brand-mark"><app-icon name="spark" /></span><span>Agent<span class="brand-light">Studio</span></span></a>
        <div class="workspace-label">PERSONAL WORKSPACE <span>01</span></div>
        <nav>
          @for (item of navigation; track item.path) {
            <a [routerLink]="item.path" routerLinkActive="active" ariaCurrentWhenActive="page" (click)="menuOpen.set(false)"><app-icon [name]="item.icon" /><span>{{item.label}}</span>@if(item.path === '/agents' && workspace.agents().length) {<span class="nav-count">{{workspace.agents().length}}</span>}</a>
          }
        </nav>
        <div class="sidebar-bottom">
          <div class="local-card"><span class="local-orbit"><app-icon name="shield" /></span><strong>Your space. Your agents.</strong><p>A local playground for ideas<br>and a little more possibility.</p><span class="local-tag"><span></span> LOCAL DEVELOPMENT</span></div>
          <div class="profile"><span class="avatar">Y</span><span><strong>Your workspace</strong><small>Personal · Mock provider</small></span><a routerLink="/settings" aria-label="Workspace settings"><app-icon name="settings" /></a></div>
        </div>
      </aside>
      <div class="main-shell">
        <header class="topbar"><div class="topbar-left"><button mat-icon-button class="mobile-menu" aria-label="Open navigation" [attr.aria-expanded]="menuOpen()" (click)="menuOpen.set(true)"><app-icon name="menu" /></button><span class="breadcrumb">Workspace <span>/</span> <strong>Personal</strong></span></div><div class="topbar-right"><span class="connection" [class.offline]="!workspace.online()"><i></i>{{workspace.loading() ? 'Connecting' : workspace.online() ? 'Backend connected' : 'Backend offline'}}</span><span class="demo-badge">MOCK MODE</span><span class="top-avatar" aria-label="Personal workspace">Y</span></div></header>
        <main #mainContent id="main-content" tabindex="-1">
          @if (workspace.error()) { <div class="connection-alert" role="alert"><span>{{workspace.error()}}</span><button mat-button (click)="workspace.load()">Reconnect</button></div> }
          <router-outlet />
        </main>
      </div>
    </div>
  `,
  styleUrl: './app.component.scss'
})
export class AppComponent {
  readonly workspace = inject(WorkspaceService);
  readonly menuOpen = signal(false);
  readonly navigation = [
    { path: '/dashboard', label: 'Dashboard', icon: 'dashboard' },
    { path: '/chat', label: 'Chat', icon: 'chat' },
    { path: '/writing', label: 'Writing', icon: 'document' },
    { path: '/agents', label: 'My Agents', icon: 'agents' },
    { path: '/projects', label: 'Projects', icon: 'folder' },
    { path: '/settings', label: 'Settings', icon: 'settings' }
  ];
}
