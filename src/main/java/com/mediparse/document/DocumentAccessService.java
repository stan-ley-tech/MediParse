package com.mediparse.document;

import com.mediparse.common.ForbiddenException;
import com.mediparse.user.Role;
import com.mediparse.user.User;
import org.springframework.stereotype.Service;

/**
 * Document-level authorization on top of role-based access: ADMIN and
 * CLINICIAN can see any patient's records, but STAFF is limited to documents
 * they personally uploaded.
 */
@Service
public class DocumentAccessService {

    public void checkCanView(User user, Document document) {
        if (hasBroadAccess(user) || document.isOwnedBy(user.getId())) {
            return;
        }
        throw new ForbiddenException("You do not have access to this document");
    }

    public void checkCanDelete(User user, Document document) {
        if (user.getRole() == Role.ADMIN || document.isOwnedBy(user.getId())) {
            return;
        }
        throw new ForbiddenException("You do not have permission to delete this document");
    }

    private boolean hasBroadAccess(User user) {
        return user.getRole() == Role.ADMIN || user.getRole() == Role.CLINICIAN;
    }
}
