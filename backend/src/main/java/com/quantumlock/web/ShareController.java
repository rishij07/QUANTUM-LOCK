package com.quantumlock.web;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.quantumlock.security.UserPrincipal;
import com.quantumlock.service.Dto.ShareRequest;
import com.quantumlock.service.Dto.ShareRow;
import com.quantumlock.service.ShareService;

@RestController
@RequestMapping("/api")
public class ShareController {
    private final ShareService shares;

    public ShareController(ShareService shares) {
        this.shares = shares;
    }

    @PostMapping("/documents/{id}/shares")
    public ShareRow share(@AuthenticationPrincipal UserPrincipal u, @PathVariable UUID id, @RequestBody ShareRequest req) {
        return shares.share(u, id, req.email(), req.permission());
    }

    @PostMapping("/shares/{id}/revoke")
    public ShareRow revoke(@AuthenticationPrincipal UserPrincipal u, @PathVariable UUID id) {
        return shares.revoke(u, id);
    }

    @GetMapping("/shares/outgoing")
    public List<ShareRow> outgoing(@AuthenticationPrincipal UserPrincipal u) {
        return shares.outgoing(u);
    }

    @GetMapping("/shares/incoming")
    public List<ShareRow> incoming(@AuthenticationPrincipal UserPrincipal u) {
        return shares.incoming(u);
    }
}
