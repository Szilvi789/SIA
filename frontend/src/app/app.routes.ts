import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  { path: 'dashboard', title: 'Dashboard · Agent Studio', loadComponent: () => import('./pages/dashboard.component').then(m => m.DashboardComponent) },
  { path: 'chat', title: 'Chat · Agent Studio', loadComponent: () => import('./pages/chat.component').then(m => m.ChatComponent) },
  { path: 'writing', title: 'Writing & Communication · Agent Studio', loadComponent: () => import('./writing/writing.component').then(m => m.WritingComponent) },
  { path: 'agents', title: 'My Agents · Agent Studio', loadComponent: () => import('./pages/agents.component').then(m => m.AgentsComponent) },
  { path: 'projects', title: 'Projects · Agent Studio', loadComponent: () => import('./pages/projects.component').then(m => m.ProjectsComponent) },
  { path: 'settings', title: 'Settings · Agent Studio', loadComponent: () => import('./pages/settings.component').then(m => m.SettingsComponent) },
  { path: '**', redirectTo: 'dashboard' }
];
