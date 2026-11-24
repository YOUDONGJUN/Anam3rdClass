package com.anam3.school;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SchoolEmailDomainRepository extends JpaRepository<SchoolEmailDomain, Long> {

    List<SchoolEmailDomain> findAllByOrderByIdAsc();

    boolean existsByDomain(String domain);

    long countBySchoolId(Long schoolId);
}
