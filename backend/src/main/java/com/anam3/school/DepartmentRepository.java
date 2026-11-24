package com.anam3.school;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    List<Department> findAllByOrderBySortOrderAscIdAsc();

    @Query("select coalesce(max(d.sortOrder), 0) from Department d where d.school.id = :schoolId")
    int maxSortOrder(@Param("schoolId") Long schoolId);
}
