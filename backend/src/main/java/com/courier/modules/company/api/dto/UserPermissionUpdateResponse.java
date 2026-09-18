package com.courier.modules.company.api.dto;

import com.courier.modules.company.application.UserPermissionService.UserPermissionUpdateResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "UserPermissionUpdateResponse")
public record UserPermissionUpdateResponse(
        List<String> granted,
        List<String> revoked,
        List<String> effectivePermissions
) {
    public static UserPermissionUpdateResponse from(UserPermissionUpdateResult result) {
        return new UserPermissionUpdateResponse(result.granted(), result.revoked(), result.effectivePermissions());
    }
}
