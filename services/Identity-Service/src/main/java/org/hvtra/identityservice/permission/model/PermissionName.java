package org.hvtra.identityservice.permission.model;

import org.hvtra.commonservice.common_core.exception.BusinessException;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Quy ước tên permission: {@code <module>:<action>} chữ thường, số và dấu gạch ngang, vd. {@code order:create}.
 * Tên là định danh ổn định (trùng tên client role trên Keycloak), không đổi sau khi tạo.
 */
public final class PermissionName {

    public static final int MAX_LENGTH = 64;
    private static final Pattern PATTERN = Pattern.compile("^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*$");

    private PermissionName() {
    }

    public static boolean isValid(String name) {
        return name != null && name.length() <= MAX_LENGTH && PATTERN.matcher(name).matches();
    }

    /** Chuẩn hóa (trim, chữ thường) rồi kiểm tra; sai thì ném BusinessException PERMISSION_NAME_INVALID. */
    public static String normalize(String rawName) {
        String name = rawName == null ? "" : rawName.trim().toLowerCase(Locale.ROOT);
        if (!isValid(name)) {
            throw new BusinessException("PERMISSION_NAME_INVALID",
                    "Tên quyền phải có dạng module:action, chỉ gồm chữ thường, số và dấu gạch ngang (vd. order:create)");
        }
        return name;
    }
}
