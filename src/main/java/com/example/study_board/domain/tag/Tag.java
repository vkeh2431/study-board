package com.example.study_board.domain.tag;

import com.example.study_board.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 태그(Phase 14). Post와 ManyToMany 관계지만 중간 엔티티 {@link PostTag}로 풀어쓴다.
 * 같은 이름의 태그는 재사용한다(name unique).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Tag extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Builder
    public Tag(String name) {
        this.name = name;
    }
}
