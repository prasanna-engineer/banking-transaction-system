package com.example.banking.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.banking.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);

	@Query("select u.tokenVersion from User u where u.id = :id")
	Optional<Integer> findTokenVersion(@Param("id") Long id);

	// Bound parameter, never string concatenation: the search text cannot inject SQL.
	@Query("""
			select u from User u
			where lower(u.email) like lower(concat('%', :q, '%'))
			   or lower(u.fullName) like lower(concat('%', :q, '%'))
			""")
	Page<User> search(@Param("q") String q, Pageable pageable);

}
