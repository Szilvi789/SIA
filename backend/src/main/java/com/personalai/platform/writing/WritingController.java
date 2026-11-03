package com.personalai.platform.writing;

import com.personalai.platform.rules.ResolvedInstructions;
import com.personalai.platform.writing.email.EmailAccount;
import com.personalai.platform.writing.email.EmailDeliveryResult;
import com.personalai.platform.writing.email.EmailPreparationService;
import com.personalai.platform.writing.email.EmailPreview;
import com.personalai.platform.writing.email.SendEmailRequest;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/writing")
public class WritingController {
    private final WritingService writing;
    private final WritingPresetService presets;
    private final DraftService drafts;
    private final EmailPreparationService email;
    public WritingController(WritingService writing, WritingPresetService presets, DraftService drafts, EmailPreparationService email) {
        this.writing = writing; this.presets = presets; this.drafts = drafts; this.email = email;
    }
    @GetMapping("/options")
    public WritingOptions options() {
        return new WritingOptions(Arrays.stream(WritingWorkflow.values()).map(workflow ->
                new WritingOptions.WorkflowOption(workflow, workflow.label(), workflow.supported())).toList(),
                presets.findAll(), email.accounts(), new WritingOptions.Capabilities("MOCK", false, "MOCK", false));
    }
    @PostMapping("/generate") public WritingResult generate(@RequestBody WritingRequest request) { return writing.generate(request); }
    @PostMapping("/preview-instructions") public ResolvedInstructions preview(@RequestBody WritingRequest request) { return writing.previewInstructions(request); }
    @GetMapping("/presets") public List<WritingPreset> presets() { return presets.findAll(); }
    @PostMapping("/presets") @ResponseStatus(HttpStatus.CREATED)
    public WritingPreset createPreset(@RequestBody WritingPreset request) { return presets.create(request); }
    @PutMapping("/presets/{id}") public WritingPreset updatePreset(@PathVariable String id, @RequestBody WritingPreset request) { return presets.update(id, request); }
    @GetMapping("/drafts") public List<WritingDraft> drafts() { return drafts.findAll(); }
    @GetMapping("/drafts/{id}") public WritingDraft draft(@PathVariable String id) { return drafts.findById(id); }
    @PostMapping("/drafts") @ResponseStatus(HttpStatus.CREATED)
    public WritingDraft createDraft(@RequestBody WritingDraft request) { return drafts.create(request); }
    @PutMapping("/drafts/{id}") public WritingDraft updateDraft(@PathVariable String id, @RequestBody WritingDraft request) { return drafts.update(id, request); }
    @GetMapping("/email/accounts") public List<EmailAccount> accounts() { return email.accounts(); }
    @DeleteMapping("/email/accounts/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect(@PathVariable String id) { email.disconnect(id); }
    @PostMapping("/email/preview") public EmailPreview emailPreview(@RequestBody EmailComposition request) { return email.preview(request); }
    @PostMapping("/email/send") public EmailDeliveryResult send(@RequestBody SendEmailRequest request) { return email.send(request); }
}
