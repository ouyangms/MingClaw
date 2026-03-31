package com.loy.mingclaw.core.workspace

import com.loy.mingclaw.core.model.workspace.ValidationResult
import com.loy.mingclaw.core.model.workspace.WorkspaceConfig

interface ConfigValidator {
    fun validate(config: WorkspaceConfig): ValidationResult
}
