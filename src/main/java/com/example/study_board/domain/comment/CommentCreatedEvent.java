package com.example.study_board.domain.comment;

/**
 * 댓글 생성 도메인 이벤트(Phase 18). {@code CommentService.create}가 댓글 저장 직후 발행하고,
 * {@code NotificationEventListener}가 커밋 후(@TransactionalEventListener AFTER_COMMIT) 비동기로 수신해 알림을 만든다.
 * 이렇게 발행자(댓글)는 알림 로직을 전혀 모르도록 <b>결합을 끊는다</b>.
 *
 * <p><b>왜 전부 원시값인가</b>: 리스너는 커밋 후 별도 스레드에서 동작해 영속성 컨텍스트가 없다. 엔티티/LAZY 프록시를
 * 넘기면 detached 접근·{@code LazyInitializationException}이 난다. 그래서 수신자 산출에 필요한 식별자/표시 문자열을
 * <b>트랜잭션 안(프록시 접근 가능)에서 미리 뽑아</b> 원시값으로만 싣는다.
 *
 * @param commentId       생성된 댓글 id
 * @param postId          댓글이 달린 게시글 id
 * @param postTitle       알림 메시지에 쓸 게시글 제목
 * @param postOwnerId     게시글 작성자 memberId(수신자 후보 1)
 * @param parentCommentId 부모 댓글 id(루트면 null)
 * @param parentOwnerId   부모 댓글 작성자 memberId(대댓글일 때만, 수신자 후보 2)
 * @param actorId         댓글 작성자 memberId(본인 예외 판정용)
 * @param actorName       댓글 작성자 사용자명(메시지용)
 */
public record CommentCreatedEvent(
        Long commentId,
        Long postId,
        String postTitle,
        Long postOwnerId,
        Long parentCommentId,
        Long parentOwnerId,
        Long actorId,
        String actorName
) {
}
