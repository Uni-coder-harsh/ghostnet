package com.ghostnet.vault;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateFolderRequest(
        @NotBlank(message = "Folder name is required")
        @Size(max = 100, message = "Folder name must not exceed 100 characters")
        String name,

        String parentId
) {}
