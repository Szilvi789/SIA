package com.personalai.platform.usage;
import java.util.List;
import org.springframework.web.bind.annotation.*;
@RestController
public class UsageController {
    private final UsageService usage;
    public UsageController(UsageService usage) { this.usage = usage; }
    @GetMapping("/api/usage") public List<UsageRecord> records() { return usage.findAll(); }
    @GetMapping("/api/usage/summary") public List<UsageSummary> summary() { return usage.summary(); }
}
