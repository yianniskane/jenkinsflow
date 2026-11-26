package com.jenkinsflow.catalog.repository;

import com.jenkinsflow.catalog.domain.Book;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BookRepository extends JpaRepository<Book, Long> {

	@Query("select b from Book b join fetch b.author order by b.id")
	List<Book> findAllWithAuthor();
}
