package com.personalai.platform.writing.infrastructure;

import com.personalai.platform.writing.WritingPreset;
import com.personalai.platform.writing.WritingPresetRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;

@Repository
public class InMemoryWritingPresetRepository implements WritingPresetRepository {
    private final ConcurrentHashMap<String, WritingPreset> presets = new ConcurrentHashMap<>();
    @Override public List<WritingPreset> findAll() { return presets.values().stream().sorted(Comparator.comparing(WritingPreset::name)).toList(); }
    @Override public Optional<WritingPreset> findById(String id) { return Optional.ofNullable(presets.get(id)); }
    @Override public WritingPreset save(WritingPreset preset) { presets.put(preset.id(), preset); return preset; }
}
