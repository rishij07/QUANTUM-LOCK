package com.quantumlock.domain;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "audit_events")
public class AuditEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    public Instant ts;
    public String userEmail;
    public String tenantName;
    public String action;
    public String resource;
    public String result;
    public String detail;
    public String prevHash;
    public String hash;

    public AuditEvent copy() {
        AuditEvent c = new AuditEvent();
        c.id = id; c.ts = ts; c.userEmail = userEmail; c.tenantName = tenantName; c.action = action;
        c.resource = resource; c.result = result; c.detail = detail; c.prevHash = prevHash; c.hash = hash;
        return c;
    }
}
