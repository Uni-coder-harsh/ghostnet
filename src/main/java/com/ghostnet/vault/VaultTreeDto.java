package com.ghostnet.vault;

import java.util.List;

public record VaultTreeDto(
        VaultFolderDto currentFolder,
        List<VaultFolderDto> breadcrumbs,
        List<VaultFolderDto> folders,
        List<VaultFileDto> files,
        long totalFiles,
        long totalFolders
) {}
