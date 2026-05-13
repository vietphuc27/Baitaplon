package server.repository.dao;

import common.models.user.User;

import java.util.List;
import java.util.Optional;

public interface UserRepository {
    void save(User user);

    Optional<User> findById(int id);

    List<User> findAll();

    void update(User user);

    void updateRoleAndStatus(User user, String role, String status);

    void delete(int id);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
