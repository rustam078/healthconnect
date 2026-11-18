package in.healthconnect.storage.service;

import in.healthconnect.storage.entity.StoredFile;
import in.healthconnect.storage.entity.enums.FileCategory;

// One per file category: decides whether a user may read a file of that category. Lets other
// modules (e.g. community chat) plug in their own rule without the storage module depending on them.
public interface FileAccessGuard {

    FileCategory category();

    boolean canAccess(StoredFile file, Integer userId);
}
