package com.ghostnet.vault;

import jakarta.validation.constraints.Size;

public record UpdateFileRequest(
        @Size(max = 255, message = "Filename must not exceed 255 characters")
        String filename,

        String folderId
) {}
