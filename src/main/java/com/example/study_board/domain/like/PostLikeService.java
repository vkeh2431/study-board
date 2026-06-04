package com.example.study_board.domain.like;

import com.example.study_board.domain.member.Member;
import com.example.study_board.domain.member.MemberRepository;
import com.example.study_board.domain.post.Post;
import com.example.study_board.domain.post.PostRepository;
import com.example.study_board.global.exception.BusinessException;
import com.example.study_board.global.exception.ErrorCode;
import com.example.study_board.global.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class PostLikeService {

    private final PostLikeRepository postLikeRepository;
    private final PostRepository postRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public void like(Long postId, Long memberId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("Post", postId));
        if (postLikeRepository.existsByMemberIdAndPostId(memberId, postId)) {
            throw new BusinessException(ErrorCode.ALREADY_LIKED); // 정상 경로 빠른 실패
        }
        // memberId는 인증된 사용자라 항상 유효 → 프록시로 FK만 채우고 추가 SELECT 없이 저장
        Member member = memberRepository.getReferenceById(memberId);
        try {
            postLikeRepository.save(PostLike.builder().member(member).post(post).build());
        } catch (DataIntegrityViolationException e) {
            // 동시 요청 race: uk_post_like가 최종 방어선. 친화적 409로 변환
            throw new BusinessException(ErrorCode.ALREADY_LIKED);
        }
        log.info("좋아요 추가: postId={}, memberId={}", postId, memberId);
    }

    @Transactional
    public void unlike(Long postId, Long memberId) {
        postLikeRepository.deleteByMemberIdAndPostId(memberId, postId); // 없으면 멱등하게 통과
        log.info("좋아요 취소: postId={}, memberId={}", postId, memberId);
    }
}
