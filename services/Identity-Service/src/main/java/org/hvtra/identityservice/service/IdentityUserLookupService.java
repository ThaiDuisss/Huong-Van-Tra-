package org.hvtra.identityservice.service;

import lombok.RequiredArgsConstructor;
import org.hvtra.identityservice.model.entity.AppUsers;
import org.hvtra.identityservice.model.response.IdentityUserBriefResponse;
import org.hvtra.identityservice.repository.IdentityUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IdentityUserLookupService {

    private final IdentityUserRepository appUsersRepository;

    /**
     * Tra cứu thông tin rút gọn của nhiều user theo Keycloak subject (chính là giá trị
     * createdBy/updatedBy mà các service khác lưu) trong 1 query. Subject chưa từng đăng nhập
     * (chưa có trong app_users) sẽ không có trong kết quả.
     */
    public List<IdentityUserBriefResponse> findBySubjects(Collection<String> keycloakSubjects) {
        if (keycloakSubjects == null || keycloakSubjects.isEmpty()) {
            return List.of();
        }

        List<String> subjects = keycloakSubjects.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(subject -> !subject.isEmpty())
                .distinct()
                .toList();
        if (subjects.isEmpty()) {
            return List.of();
        }

        // Cùng subject có thể tồn tại dưới nhiều issuer — giữ bản ghi đầu tiên để kết quả không bị trùng
        Map<String, IdentityUserBriefResponse> bySubject = new LinkedHashMap<>();
        for (AppUsers user : appUsersRepository.findByKeycloakSubjectIn(subjects)) {
            bySubject.putIfAbsent(user.getKeycloakSubject(), new IdentityUserBriefResponse(
                    user.getKeycloakSubject(),
                    user.getFullNameSnapshot(),
                    user.getUsernameSnapshot(),
                    user.getEmailSnapshot()
            ));
        }
        return List.copyOf(bySubject.values());
    }
}
