package com.personalai.platform.usage;
import java.util.List;
public interface UsageRepository {
    UsageRecord save(UsageRecord record);
    List<UsageRecord> findAll();
}
