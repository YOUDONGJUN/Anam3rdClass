package com.anam3.school;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SchoolRepository extends JpaRepository<School, Long> {

    List<School> findAllByOrderBySortOrderAscIdAsc();

    Optional<School> findByCode(String code);

    boolean existsByName(String name);
}
