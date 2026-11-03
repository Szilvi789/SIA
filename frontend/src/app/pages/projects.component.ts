import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { IconComponent } from '../shared/icon.component';

@Component({
  standalone: true,
  imports: [RouterLink, MatButtonModule, IconComponent],
  template: `
    <div class="page-title"><div><div class="eyebrow intro-label">GOOD IDEAS DESERVE A HOME</div><h1>A place for the bigger picture.</h1><p>Your projects will bring agents, conversations, and context together.</p></div><span class="tag roadmap-tag">ON THE ROADMAP</span></div>
    <section class="projects-empty panel"><div class="folder-illustration" aria-hidden="true"><span class="folder-back"></span><span class="folder-front"><app-icon name="spark" /></span><span class="folder-dot"></span></div><span class="eyebrow">A LITTLE FURTHER DOWN THE ROAD</span><h2>Room for what comes next.</h2><p>Projects are part of the next chapter. For now, get to know your agents<br class="desktop-break"> and explore a few ideas in a conversation.</p><a mat-flat-button routerLink="/chat" class="button-icon">Explore your workspace <app-icon name="arrow" /></a><span class="future-note">Project creation and storage are not available in this release.</span></section>
    <div class="section-heading"><h2>What we are making room for</h2><span class="roadmap-note">PLANNED · NOT YET AVAILABLE</span></div><div class="future-grid"><article class="panel"><span class="future-icon"><app-icon name="folder" /></span><h3>A home for each idea</h3><p>Keep related conversations together, with a little less searching and a little more focus.</p></article><article class="panel"><span class="future-icon"><app-icon name="agents" /></span><h3>The right team for the job</h3><p>Bring a few of your agents into a project and give each one a clear role.</p></article><article class="panel"><span class="future-icon"><app-icon name="document" /></span><h3>Context that stays with you</h3><p>Save project instructions and history so your next conversation has a head start.</p></article></div>
  `,
  styleUrl: './projects.component.scss'
})
export class ProjectsComponent {}
