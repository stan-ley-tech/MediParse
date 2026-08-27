package com.mediparse.document;

import com.mediparse.common.ForbiddenException;
import com.mediparse.user.Role;
import com.mediparse.user.User;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentAccessServiceTest {

    private final DocumentAccessService accessService = new DocumentAccessService();

    @Test
    void adminCanViewAnyDocument() {
        User admin = userWithId(Role.ADMIN);
        Document document = documentOwnedBy(UUID.randomUUID());

        assertThatCode(() -> accessService.checkCanView(admin, document)).doesNotThrowAnyException();
    }

    @Test
    void clinicianCanViewAnyDocument() {
        User clinician = userWithId(Role.CLINICIAN);
        Document document = documentOwnedBy(UUID.randomUUID());

        assertThatCode(() -> accessService.checkCanView(clinician, document)).doesNotThrowAnyException();
    }

    @Test
    void staffCanViewTheirOwnUpload() {
        User staff = userWithId(Role.STAFF);
        Document document = documentOwnedBy(staff.getId());

        assertThatCode(() -> accessService.checkCanView(staff, document)).doesNotThrowAnyException();
    }

    @Test
    void staffCannotViewSomeoneElsesUpload() {
        User staff = userWithId(Role.STAFF);
        Document document = documentOwnedBy(UUID.randomUUID());

        assertThatThrownBy(() -> accessService.checkCanView(staff, document))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void staffCanDeleteTheirOwnUploadButNotSomeoneElses() {
        User staff = userWithId(Role.STAFF);
        Document own = documentOwnedBy(staff.getId());
        Document someoneElses = documentOwnedBy(UUID.randomUUID());

        assertThatCode(() -> accessService.checkCanDelete(staff, own)).doesNotThrowAnyException();
        assertThatThrownBy(() -> accessService.checkCanDelete(staff, someoneElses))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void clinicianCannotDeleteSomeoneElsesUpload() {
        User clinician = userWithId(Role.CLINICIAN);
        Document document = documentOwnedBy(UUID.randomUUID());

        assertThatThrownBy(() -> accessService.checkCanDelete(clinician, document))
                .isInstanceOf(ForbiddenException.class);
    }

    private User userWithId(Role role) {
        User user = new User("user@example.com", "hashed", "Test User", role);
        user.setId(UUID.randomUUID());
        return user;
    }

    private Document documentOwnedBy(UUID uploadedBy) {
        return new Document(null, uploadedBy, "file.pdf", "application/pdf", 100, "hash", "path", null);
    }
}
