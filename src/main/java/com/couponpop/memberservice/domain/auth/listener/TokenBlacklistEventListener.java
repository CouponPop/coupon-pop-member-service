package com.couponpop.memberservice.domain.auth.listener;

import com.couponpop.memberservice.domain.auth.event.TokenBlacklistEvent;
import com.couponpop.security.blacklist.service.TokenBlacklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class TokenBlacklistEventListener {

    private final TokenBlacklistService tokenBlacklistService;

    /**
     * 회원 탈퇴의 DB 작업이 롤백되면 JWT 만료는 실행되지 않도록
     * 트랜잭션 커밋 이후에 JWT 블랙리스트 작업을 수행합니다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleTokenBlacklist(TokenBlacklistEvent event) {

        tokenBlacklistService.blacklistToken(event.token(), event.expirationMillis());
        log.debug("[handleTokenBlacklist] 토큰 블랙리스트 이벤트 수행 완료 - token={}", event.token());
    }
}