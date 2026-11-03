package com.codedoctor.repository;

import com.codedoctor.model.CodeReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CodeReviewRepository extends JpaRepository<CodeReview, Long> {

    @Query("""
           select r from CodeReview r
           where lower(r.title) like lower(concat('%', :q, '%'))
              or lower(r.summary) like lower(concat('%', :q, '%'))
              or lower(r.language) like lower(concat('%', :q, '%'))
           """)
    Page<CodeReview> search(@Param("q") String q, Pageable pageable);
}
