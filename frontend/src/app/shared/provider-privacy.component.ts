import { Component, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService, errorMessage } from '../core/api.service';
import { ProviderDataPermission } from '../core/models';

@Component({
  selector: 'app-provider-privacy',
  standalone: true,
  imports: [FormsModule],
  template: `
    <section class="privacy-panel panel">
      <h2>Cloud data sharing</h2>
      <p>Running SAI locally does not keep cloud API requests on your device. Local retrieval and sending selected passages to a provider are separate actions.</p>
      <p>Permissions below allow categories for a provider. Each generation also requires consent for that request and its effective provider. Credentials and OAuth tokens belong in a private server configuration or credential store.</p>
      @if(!personal()){<p><strong>Demo mode blocks cloud requests.</strong> Start your separate personal environment to configure permissions.</p>}
      @if(error()){<p class="error-message" role="alert">{{error()}}</p>}
      @for(permission of permissions(); track permission.providerId){
        @if(permission.providerId !== 'mock'){
          <fieldset [disabled]="!personal() || saving()">
            <legend>{{permission.providerId}}</legend>
            <label><input type="checkbox" [(ngModel)]="permission.enabled"> Allow this provider to receive authorized content</label>
            <label><input type="checkbox" [(ngModel)]="permission.taskContent"> Task text and email fields</label>
            <label><input type="checkbox" [(ngModel)]="permission.personalInstructions"> Agent instructions, private rules, and preferences</label>
            <label><input type="checkbox" [(ngModel)]="permission.knowledgePassages"> Selected passages from explicitly authorized knowledge sources</label>
            <button type="button" (click)="save(permission)">Save {{permission.providerId}} permissions</button>
          </fieldset>
        }
      }
      <p role="status">{{status()}}</p>
    </section>
  `,
  styles: `
    .privacy-panel { padding:25px; margin:24px 0; font-size:13px; color:#53634f; }
    h2 { color:#243f2f; margin:0 0 12px; } p { line-height:1.65; }
    fieldset { border:1px solid #dce4d6; border-radius:10px; margin:18px 0; padding:15px; }
    legend { font-weight:600; color:#294b36; padding:0 6px; }
    label { display:flex; align-items:flex-start; gap:8px; margin:10px 0; }
    button { background:#edf2e9; color:#294b36; border:1px solid #d7e0d1; border-radius:6px; padding:8px 12px; cursor:pointer; }
  `
})
export class ProviderPrivacyComponent {
  readonly personal = input(false);
  private readonly api = inject(ApiService);
  readonly permissions = signal<ProviderDataPermission[]>([]);
  readonly error = signal('');
  readonly status = signal('');
  readonly saving = signal(false);
  constructor() {
    this.api.providerDataPermissions().subscribe({ next: permissions => this.permissions.set(permissions), error: error => this.error.set(errorMessage(error)) });
  }
  save(permission: ProviderDataPermission): void {
    if (!this.personal() || this.saving()) return;
    this.saving.set(true); this.error.set(''); this.status.set('');
    this.api.updateProviderDataPermission({ ...permission }).subscribe({
      next: saved => { this.permissions.update(list => list.map(item => item.providerId === saved.providerId ? saved : item)); this.saving.set(false); this.status.set('Provider permissions saved in your private database.'); },
      error: error => { this.error.set(errorMessage(error)); this.saving.set(false); }
    });
  }
}
