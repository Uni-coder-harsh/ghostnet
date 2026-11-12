package com.ghostnet.vault;

import org.springframework.core.io.Resource;

public record VaultFileDownload(
        VaultFileRecord file,
        Resource resource
) {}
