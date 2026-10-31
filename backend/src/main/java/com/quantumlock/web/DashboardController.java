package com.quantumlock.web;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.quantumlock.config.QlockProperties;
import com.quantumlock.kms.KmsService;
import com.quantumlock.security.UserPrincipal;
import com.quantumlock.service.AuditService;
import com.quantumlock.service.AuthService;
import com.quantumlock.service.Dto.DashboardView;
import com.quantumlock.service.Dto.DocView;
import com.quantumlock.service.Dto.ShareRow;
import com.quantumlock.service.DocumentService;
import com.quantumlock.service.ShareService;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final DocumentService documents;
    private final ShareService shares;
    private final AuditService audit;
    private final KmsService kms;
    private final QlockProperties props;

    public DashboardController(DocumentService documents, ShareService shares, AuditService audit, KmsService kms, QlockProperties props) {
        this.documents = documents;
        this.shares = shares;
        this.audit = audit;
        this.kms = kms;
        this.props = props;
    }

    @GetMapping
    public DashboardView dashboard(@AuthenticationPrincipal UserPrincipal u) {
        List<DocView> docs = documents.list(u);
        long sharedWithMe = shares.incoming(u).stream().filter(s -> "ACTIVE".equals(s.status())).count();
        long sharedByMe = shares.outgoing(u).stream().filter(s -> "ACTIVE".equals(s.status())).count();
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
        long bad = audit.countSince(u.tenantName(), since, List.of("DENIED", "BLOCKED", "DETECTED"));
        long all = audit.countSince(u.tenantName(), since, List.of("SUCCESS", "DENIED", "BLOCKED", "DETECTED"));
        return new DashboardView(AuthService.info(u), docs.size(), sharedWithMe, sharedByMe, bad, all,
                docs.stream().limit(5).toList(), audit.list(u, 6), audit.verify(), kms.activeKeyId(), props.demoMode());
    }
}
