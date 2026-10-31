package com.quantumlock.web;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.quantumlock.security.UserPrincipal;
import com.quantumlock.service.AuditService;
import com.quantumlock.service.Dto.AuditRow;
import com.quantumlock.service.Dto.ChainStatus;
import com.quantumlock.service.Dto.TamperSimulation;

@RestController
@RequestMapping("/api/audit")
public class AuditController {
    private final AuditService audit;

    public AuditController(AuditService audit) {
        this.audit = audit;
    }

    /** Admins see their tenant's events; employees see only their own. */
    @GetMapping
    public List<AuditRow> list(@AuthenticationPrincipal UserPrincipal u, @RequestParam(defaultValue = "100") int limit) {
        return audit.list(u, limit);
    }

    @GetMapping("/verify")
    public ChainStatus verify() {
        return audit.verify();
    }

    @PostMapping("/simulate-tamper")
    public TamperSimulation simulate() {
        return audit.simulateTamper();
    }
}
