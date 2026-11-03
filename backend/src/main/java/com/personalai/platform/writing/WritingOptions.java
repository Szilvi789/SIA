package com.personalai.platform.writing;

import com.personalai.platform.writing.email.EmailAccount;
import java.util.List;

public record WritingOptions(List<WorkflowOption> workflows, List<WritingPreset> presets,
        List<EmailAccount> accounts, Capabilities capabilities) {
    public record WorkflowOption(WritingWorkflow id, String label, boolean supported) { }
    public record Capabilities(String generation, boolean sending, String recipientDiscovery, boolean attachments) { }
}
