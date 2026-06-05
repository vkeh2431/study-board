package com.example.study_board.domain.tag;

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
class TagRepositoryTest {

    @Autowired
    private TagRepository tagRepository;

    @Test
    @DisplayName("태그명으로 조회 - 존재하면 반환 (태그 재사용 경로)")
    void findByName_returns_tag_when_present() {
        tagRepository.save(Tag.builder().name("spring").build());

        Optional<Tag> found = tagRepository.findByName("spring");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("spring");
    }

    @Test
    @DisplayName("태그명으로 조회 - 없으면 빈 Optional (신규 생성 경로)")
    void findByName_returns_empty_when_absent() {
        Optional<Tag> found = tagRepository.findByName("none");

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("같은 이름 태그는 유니크 제약으로 중복 저장이 막힌다")
    void duplicate_name_violates_unique_constraint() {
        tagRepository.saveAndFlush(Tag.builder().name("java").build());

        assertThatThrownBy(() -> tagRepository.saveAndFlush(Tag.builder().name("java").build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
