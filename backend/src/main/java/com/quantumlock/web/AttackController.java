package com.quantumlock.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.quantumlock.config.ApiException;
import com.quantumlock.config.QlockProperties;
import com.quantumlock.repo.DocumentRepository;
import com.quantumlock.repo.TenantRepository;
import com.quantumlock.security.UserPrincipal;
import com.quantumlock.service.AttackService;
import com.quantumlock.service.Dto.AttackResult;
import com.quantumlock.service.Dto.ForeignDoc;

@RestController
@RequestMapping("/api")
public class AttackController {
    private final AttackService attacks;
    private final DocumentRepository docs;
    private final TenantRepository tenants;
    private final QlockProperties props;

    public AttackController(AttackService attacks, DocumentRepository docs, TenantRepository tenants, QlockProperties props) {
        this.attacks = attacks;
        this.docs = docs;
        this.tenants = tenants;
        this.props = props;
    }

    @PostMapping("/attacks/{type}")
    public AttackResult run(@AuthenticationPrincipal UserPrincipal u, @PathVariable String type) {
        return attacks.run(u, type);
    }

    /**
     * DEMO HELPER ONLY (QL_DEMO_MODE=true): lists document IDs from other tenants so judges can paste one into the probe box.
     * This deliberately breaks "no enumeration" to make the isolation demo easy; disable in any real deployment.
     */
    @GetMapping("/demo/foreign-docs")
    public List<ForeignDoc> foreign(@AuthenticationPrincipal UserPrincipal u) {
        if (!props.demoMode()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Not found");
        }
        UUID sys = tenants.findBySlug(AttackService.SYSTEM_SLUG).map(t -> t.id).orElse(null);
        var names = new java.util.HashMap<UUID, String>();
        tenants.findAll().forEach(t -> names.put(t.id, t.name));
        return docs.findByTenantIdNot(u.tenantId()).stream().filter(d -> !d.tenantId.equals(sys))
                .map(d -> new ForeignDoc(d.id, d.name, names.get(d.tenantId))).toList();
    }
}
