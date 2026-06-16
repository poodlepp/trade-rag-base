package com.trade.ragbase.service;

import java.util.List;

import com.trade.ragbase.entity.KnowledgeBase;
import com.trade.ragbase.exception.BizException;
import com.trade.ragbase.repository.KbPermissionRepository;
import com.trade.ragbase.repository.KnowledgeBaseRepository;
import com.trade.ragbase.security.UserContext;
import org.springframework.stereotype.Service;

@Service
public class PermissionService {

    private final KbPermissionRepository permissionRepository;
    private final KnowledgeBaseRepository kbRepository;

    public PermissionService(KbPermissionRepository permissionRepository, KnowledgeBaseRepository kbRepository) {
        this.permissionRepository = permissionRepository;
        this.kbRepository = kbRepository;
    }

    public void requireRead(Long kbId) {
        if (UserContext.isAdmin()) {
            return;
        }

        boolean isPublic = kbRepository.findById(kbId)
                .map(KnowledgeBase::getIsPublic)
                .orElse(false);
        if (isPublic) {
            return;
        }

        String userId = String.valueOf(UserContext.getUserId());
        String deptId = UserContext.getDepartmentId();
        boolean hasPermission = permissionRepository.existsByKbIdAndSubjectTypeAndSubjectId(
                kbId, "USER", userId)
                || permissionRepository.existsByKbIdAndSubjectTypeAndSubjectId(
                kbId, "DEPARTMENT", deptId);

        if (!hasPermission) {
            throw BizException.forbidden("无权访问该知识库");
        }
    }

    public void requireWrite(Long kbId) {
        if (UserContext.isAdmin()) {
            return;
        }

        String userId = String.valueOf(UserContext.getUserId());
        String deptId = UserContext.getDepartmentId();
        List<String> writePermissions = List.of("WRITE", "ADMIN");
        boolean hasWritePermission = permissionRepository.existsByKbIdAndSubjectTypeAndSubjectIdAndPermissionIn(
                kbId, "USER", userId, writePermissions)
                || permissionRepository.existsByKbIdAndSubjectTypeAndSubjectIdAndPermissionIn(
                kbId, "DEPARTMENT", deptId, writePermissions);

        if (!hasWritePermission) {
            throw BizException.forbidden("无文档管理权限");
        }
    }
}
