package com.anam3.safety;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.anam3.auth.LoginMember;
import com.anam3.safety.SafetyDtos.BlockRequest;
import com.anam3.safety.SafetyDtos.BlockView;
import com.anam3.safety.SafetyDtos.ReportRequest;

/** 신고·차단 (로그인한 누구나). 신고 처리는 관리 API (AdminController) */
@RestController
@RequestMapping("/api")
public class SafetyController {

    private final ReportService reportService;
    private final BlockService blockService;

    public SafetyController(ReportService reportService, BlockService blockService) {
        this.reportService = reportService;
        this.blockService = blockService;
    }

    @PostMapping("/reports")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void report(@AuthenticationPrincipal LoginMember me, @Valid @RequestBody ReportRequest req) {
        reportService.report(me, req);
    }

    @GetMapping("/blocks")
    public List<BlockView> blocks(@AuthenticationPrincipal LoginMember me) {
        return blockService.list(me);
    }

    @PostMapping("/blocks")
    @ResponseStatus(HttpStatus.CREATED)
    public BlockView block(@AuthenticationPrincipal LoginMember me, @Valid @RequestBody BlockRequest req) {
        return blockService.block(me, req.ref(), Boolean.TRUE.equals(req.left()));
    }

    @DeleteMapping("/blocks/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unblock(@AuthenticationPrincipal LoginMember me, @PathVariable Long id) {
        blockService.unblock(me, id);
    }
}
