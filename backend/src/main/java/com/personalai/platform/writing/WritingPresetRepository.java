package com.personalai.platform.writing;

import java.util.List;
import java.util.Optional;

public interface WritingPresetRepository {
    List<WritingPreset> findAll();
    Optional<WritingPreset> findById(String id);
    WritingPreset save(WritingPreset preset);
}
