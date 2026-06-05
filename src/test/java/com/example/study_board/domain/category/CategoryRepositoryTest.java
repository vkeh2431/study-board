package com.example.study_board.domain.category;

import com.example.study_board.global.config.JpaAuditingConfig;
import com.example.study_board.global.config.QueryDslConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import({JpaAuditingConfig.class, QueryDslConfig.class})
@ActiveProfiles("test")
class CategoryRepositoryTest {

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    @DisplayName("카테고리 저장 후 단건 조회")
    void save_and_find_category() {
        Category saved = categoryRepository.save(Category.builder().name("스프링").build());

        Optional<Category> found = categoryRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("스프링");
        assertThat(found.get().getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("같은 이름 카테고리는 유니크 제약으로 중복 저장이 막힌다")
    void duplicate_name_violates_unique_constraint() {
        categoryRepository.saveAndFlush(Category.builder().name("JPA").build());

        assertThatThrownBy(() -> categoryRepository.saveAndFlush(Category.builder().name("JPA").build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
