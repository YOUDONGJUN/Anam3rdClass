package com.anam3.ranking;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.anam3.auth.LoginMember;
import com.anam3.ranking.RankingDtos.RankingView;

/** 랭킹 (로그인한 원우 누구나) */
@RestController
@RequestMapping("/api/rankings")
public class RankingController {

    private final RankingService rankingService;

    public RankingController(RankingService rankingService) {
        this.rankingService = rankingService;
    }

    /** period: month(기본) | all · scope: all(연합, 기본) | school(우리 학교). 모르는 값은 기본으로 본다 */
    @GetMapping
    public RankingView ranking(@AuthenticationPrincipal LoginMember me,
            @RequestParam(defaultValue = "month") String period, @RequestParam(defaultValue = "all") String scope) {
        return rankingService.ranking(me, "all".equals(period), "school".equals(scope));
    }
}
