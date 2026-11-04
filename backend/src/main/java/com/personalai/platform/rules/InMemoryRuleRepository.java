package com.personalai.platform.rules;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import com.personalai.platform.common.InvalidConfigurationException;
import org.springframework.stereotype.Repository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** Loads only packaged, explicitly allowlisted rules; request data never supplies a path. */
@Repository
@ConditionalOnProperty(name = "sai.mode", havingValue = "demo", matchIfMissing = true)
public class InMemoryRuleRepository implements RuleRepository {
    private final Map<String, RuleDocument> rules;

    public InMemoryRuleRepository() {
        rules = new ConcurrentHashMap<>(List.of(
                load("global-instructions", RuleScope.GLOBAL),
                load("coding-rules", RuleScope.AGENT),
                load("email-style", RuleScope.AGENT),
                load("translation-rules", RuleScope.AGENT),
                load("documentation-guidelines", RuleScope.AGENT),
                load("writing-core", RuleScope.AGENT),
                load("writing-general", RuleScope.AGENT),
                load("writing-email", RuleScope.AGENT),
                load("writing-translation", RuleScope.AGENT),
                load("writing-style-professional", RuleScope.AGENT),
                load("writing-style-friendly", RuleScope.AGENT),
                load("writing-style-diplomatic", RuleScope.AGENT),
                load("writing-language-en", RuleScope.AGENT),
                load("writing-language-de", RuleScope.AGENT),
                load("writing-language-hu", RuleScope.AGENT))
                .stream().collect(Collectors.toMap(RuleDocument::id, Function.identity())));
    }

    @Override
    public List<RuleDocument> findAll() {
        return rules.values().stream().sorted(Comparator.comparing(RuleDocument::id)).toList();
    }

    @Override
    public Optional<RuleDocument> findById(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(rules.get(id));
    }

    @Override
    public RuleDocument saveUserOwned(RuleDocument rule) {
        if (rule == null || !rule.id().matches("writing-user-[a-z0-9-]{1,60}") || rule.scope() != RuleScope.AGENT) {
            throw new InvalidConfigurationException("Only user-owned writing preset rules can be edited.");
        }
        rules.put(rule.id(), rule);
        return rule;
    }

    private static RuleDocument load(String id, RuleScope scope) {
        String filename = id + ".md";
        try (var stream = InMemoryRuleRepository.class.getResourceAsStream("/rules/" + filename)) {
            if (stream == null) {
                throw new IllegalStateException("Missing packaged rule: " + filename);
            }
            byte[] bytes = stream.readNBytes(RuleDocument.MAX_CONTENT_LENGTH * 4 + 1);
            if (bytes.length > RuleDocument.MAX_CONTENT_LENGTH * 4) {
                throw new IllegalStateException("Packaged rule exceeds the content limit: " + filename);
            }
            return new RuleDocument(id, filename, scope, new String(bytes, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read packaged rule: " + filename, exception);
        }
    }
}
