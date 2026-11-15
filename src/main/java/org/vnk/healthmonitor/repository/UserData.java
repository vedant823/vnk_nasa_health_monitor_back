package org.vnk.healthmonitor.repository;

import org.vnk.healthmonitor.domain.document.UserDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface UserData extends MongoRepository<UserDocument, String> {

    Optional<UserDocument> findByUsername(String username);

    Optional<UserDocument> findByToken(String token);

    /** The children currently assigned to an officer, for GET /users/children. */
    List<UserDocument> findByOfficerId(String officerId);
}
