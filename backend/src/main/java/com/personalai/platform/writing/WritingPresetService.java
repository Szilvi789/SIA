package com.personalai.platform.writing;

import com.personalai.platform.common.InvalidConfigurationException;
import com.personalai.platform.common.ResourceNotFoundException;
import com.personalai.platform.rules.RuleDocument;
import com.personalai.platform.rules.RuleRepository;
import com.personalai.platform.rules.RuleScope;
import com.personalai.platform.rules.RuleService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class WritingPresetService {
    private final WritingPresetRepository repository;
    private final RuleRepository rules;
    private final RuleService ruleService;
    public WritingPresetService(WritingPresetRepository repository, RuleRepository rules, RuleService ruleService) {
        this.repository = repository; this.rules = rules; this.ruleService = ruleService;
        seed("professional", "Professional", "Clear, respectful communication.", WritingWorkflow.GENERAL,
                "writing-style-professional", "Use clear professional language.");
        seed("friendly-email", "Friendly email", "Warm, approachable email drafts.", WritingWorkflow.EMAIL,
                "writing-style-friendly", "Use a friendly greeting and respectful closing when appropriate.");
        seed("diplomatic-email", "Diplomatic email", "Constructive, measured communication.", WritingWorkflow.EMAIL,
                "writing-style-diplomatic", "Explain concerns constructively without assuming facts.");
        seed("natural-translation", "Natural translation", "Meaning and idiom over word-for-word phrasing.", WritingWorkflow.TRANSLATION,
                "writing-translation", "Preserve meaning and use natural phrasing in the output language.");
    }
    private void seed(String id, String name, String description, WritingWorkflow workflow, String ruleId, String markdown) {
        if (repository.findById(id).isEmpty()) store(new WritingPreset(id, name, description, workflow, true, List.of(ruleId), markdown, null));
    }
    public List<WritingPreset> findAll() { return repository.findAll(); }
    public WritingPreset findById(String id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Writing preset was not found."));
    }
    public synchronized WritingPreset create(WritingPreset preset) {
        if (repository.findById(preset.id()).isPresent()) throw new InvalidConfigurationException("id", "A preset with this ID already exists.");
        return store(preset);
    }
    public synchronized WritingPreset update(String id, WritingPreset preset) {
        findById(id);
        if (!id.equals(preset.id())) throw new InvalidConfigurationException("id", "A preset ID cannot change.");
        return store(preset);
    }
    private WritingPreset store(WritingPreset preset) {
        ruleService.validateAssignments(preset.ruleIds());
        if (preset.ruleIds().stream().anyMatch(id -> id.startsWith("writing-user-"))) {
            throw new InvalidConfigurationException("ruleIds", "A preset may not reference another preset's editable Markdown.");
        }
        rules.saveUserOwned(new RuleDocument(markdownRuleId(preset), markdownRuleId(preset) + ".md", RuleScope.AGENT, preset.markdown()));
        return repository.save(preset);
    }
    public String markdownRuleId(WritingPreset preset) { return "writing-user-" + preset.id(); }
    public WritingPreset requireApplicable(String id, WritingWorkflow workflow) {
        var preset = findById(id);
        if (!preset.enabled()) throw new InvalidConfigurationException("presetId", "This writing preset is disabled.");
        if (preset.workflow() != workflow && preset.workflow() != WritingWorkflow.GENERAL) {
            throw new InvalidConfigurationException("presetId", "This preset belongs to another workflow.");
        }
        return preset;
    }
}
