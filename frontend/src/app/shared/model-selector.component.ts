import { Component, computed, input, output } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Agent, AIModel, GlobalModelSettings, ModelSelection } from '../core/models';

/** This control changes task/conversation state only; it never saves an agent. */
@Component({
  selector: 'app-model-selector',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="model-selector">
      <label>Model for this {{scope()}}
        <select [ngModel]="choice()" (ngModelChange)="choose($event)" [disabled]="disabled()">
          <option value="default">Use agent default</option>
          @for(tier of tiers; track tier){<option [value]="'tier:' + tier">{{tierLabel(tier)}}</option>}
          @if(explicitModel(); as id){<option [value]="'model:' + id">Specific: {{modelName(id)}}</option>}
        </select>
      </label>
      <details>
        <summary>Advanced model selection</summary>
        <label>Registered model
          <select [ngModel]="explicitModel()" (ngModelChange)="choose('model:' + $event)" [disabled]="disabled()">
            <option value="" disabled>Choose a specific model</option>
            @for(model of compatibleModels(); track model.id){<option [value]="model.id">{{model.name}} · {{model.providerId}}</option>}
          </select>
        </label>
        <p>Tiers are SAI presets mapped to configured models. Mock tiers select demonstration templates.</p>
      </details>
      @if(effectiveModel(); as model){
        <p class="model-detail"><strong>{{model.name}}</strong> · Provider: {{model.providerId}} · {{model.description}}</p>
        <p class="model-detail">Capabilities: {{model.capabilities?.join(', ') || 'Unknown'}}.
          Structured output: {{capability(model.structuredOutput)}} · Tools: {{capability(model.toolCalling)}} · Vision: {{capability(model.vision)}}</p>
        <p class="model-detail">{{pricingText(model)}} {{outputEstimate(model)}}</p>
        @if(model.providerId !== 'mock'){
          <div class="sharing-notice">
            <p><strong>Local app, separate provider.</strong> This model may send task text, email fields, agent instructions, and preferences outside your device. The adapter must declare its processing location; unknown locations are blocked.</p>
            <label class="sharing-check"><input type="checkbox" [checked]="taskAuthorized(model.providerId)" [disabled]="disabled()" (change)="authorizeTask(model.providerId, $any($event.target).checked)"> Authorize this request's task and instructions for {{model.providerId}}.</label>
            @if(agent()?.knowledgeSourceIds?.length){<label class="sharing-check"><input type="checkbox" [checked]="!!selection().dataSharingConsent?.knowledgeSourceIds?.length" [disabled]="disabled() || !taskAuthorized(model.providerId)" (change)="authorizeKnowledge(model.providerId, $any($event.target).checked)"> Also authorize selected passages from this agent's assigned knowledge sources.</label>}
            <p>Local retrieval alone does not authorize cloud sharing. Personal mode and saved provider permissions are required. Consent clears after generation and when the model changes.</p>
          </div>
        }
      } @else {
        <p class="model-error" role="status">{{configurationMessage()}}</p>
      }
      <div class="selection-foot"><span>Applies to this {{scope()}}. Agent defaults are saved in My Agents.</span>
        @if(choice() !== 'default'){<button type="button" (click)="choose('default')" [disabled]="disabled()">Restore default</button>}
      </div>
    </div>
  `,
  styleUrl: './model-selector.component.scss'
})
export class ModelSelectorComponent {
  readonly agent = input<Agent>();
  readonly models = input<AIModel[]>([]);
  readonly settings = input<GlobalModelSettings>({ defaultModelId: '', tierMappings: {} });
  readonly selection = input<ModelSelection>({});
  readonly disabled = input(false);
  readonly scope = input('task');
  readonly requiredCapabilities = input<string[]>(['TEXT']);
  readonly selectionChange = output<ModelSelection>();
  readonly tiers = ['ECONOMY', 'BALANCED', 'ADVANCED'];
  readonly explicitModel = computed(() => this.selection().modelId || this.selection().overrideModelId || '');
  readonly selectedTier = computed(() => this.selection().tier || this.selection().overrideTier || '');
  readonly choice = computed(() => this.explicitModel() ? `model:${this.explicitModel()}` : this.selectedTier() ? `tier:${this.selectedTier()}` : 'default');
  readonly compatibleModels = computed(() => this.models().filter(model => this.isCompatible(model)));
  readonly effectiveModelId = computed(() => {
    if (this.explicitModel()) return this.explicitModel();
    const agent = this.agent();
    const tier = this.selectedTier() || agent?.modelSettings?.defaultTier;
    if (tier) return agent?.modelSettings?.tierMappings?.[tier] || this.settings().tierMappings[tier] || '';
    return agent?.modelId || this.settings().defaultModelId || '';
  });
  readonly effectiveModel = computed(() => this.models().find(model => model.id === this.effectiveModelId() && this.isCompatible(model)));

  choose(choice: string): void {
    if (this.disabled()) return;
    this.selectionChange.emit(choice.startsWith('tier:') ? { overrideTier: choice.slice(5) }
      : choice.startsWith('model:') ? { overrideModelId: choice.slice(6) } : {});
  }
  taskAuthorized(providerId: string): boolean {
    const consent = this.selection().dataSharingConsent;
    return consent?.providerId === providerId && consent.taskContent && consent.personalInstructions;
  }
  authorizeTask(providerId: string, allowed: boolean): void {
    this.selectionChange.emit({ ...this.selection(), dataSharingConsent: allowed
      ? { providerId, taskContent: true, personalInstructions: true, knowledgeSourceIds: [] } : null });
  }
  authorizeKnowledge(providerId: string, allowed: boolean): void {
    this.selectionChange.emit({ ...this.selection(), dataSharingConsent: { providerId, taskContent: true, personalInstructions: true,
      knowledgeSourceIds: allowed ? [...(this.agent()?.knowledgeSourceIds ?? [])] : [] } });
  }
  tierLabel(tier: string): string { return tier[0] + tier.slice(1).toLowerCase(); }
  modelName(id: string): string { return this.models().find(model => model.id === id)?.name || id; }
  capability(value: boolean | null | undefined): string { return value == null ? 'Unknown' : value ? 'Supported' : 'Unsupported'; }
  configurationMessage(): string {
    if (this.selectedTier() || this.agent()?.modelSettings?.defaultTier) return 'This tier needs an enabled, compatible model configured in Settings or My Agents.';
    return 'Configure an enabled, compatible model in Settings or restore a valid agent default.';
  }
  pricingText(model: AIModel): string {
    if (model.providerId === 'mock') return 'Mock execution · No API billing or measured token usage.';
    if (model.inputTokenPrice == null || model.outputTokenPrice == null || !model.currency || !model.pricingUnit || !model.pricingUpdatedAt) return 'Estimated cost unknown · Complete timestamped pricing is not configured.';
    return `Configured rates: ${model.inputTokenPrice} input / ${model.outputTokenPrice} output ${model.currency} per ${model.pricingUnit} tokens (updated ${model.pricingUpdatedAt}).`;
  }
  outputEstimate(model: AIModel): string {
    if (model.providerId === 'mock' || model.outputTokenPrice == null || !model.currency || !model.pricingUnit || !model.pricingUpdatedAt) return '';
    const limit = this.agent()?.modelSettings?.parameters?.maxOutputTokens ?? this.agent()?.maxOutputTokens;
    if (!limit) return 'Request estimate unknown until token usage is available.';
    return `Output allowance estimate: up to ${(limit * model.outputTokenPrice / model.pricingUnit).toFixed(6)} ${model.currency}; input charges are additional. This is not a billing quote.`;
  }
  private isCompatible(model: AIModel): boolean {
    return model.enabled !== false && this.requiredCapabilities().every(capability => model.capabilities?.includes(capability) === true)
      && (!this.requiredCapabilities().includes('STRUCTURED_OUTPUT') || model.structuredOutput === true);
  }
}
