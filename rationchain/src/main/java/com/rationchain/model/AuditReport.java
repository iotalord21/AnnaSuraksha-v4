package com.rationchain.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_reports")
public class AuditReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer totalScanned;
    private Integer ghostsFound;
    private Integer verifiedCount;
    private Integer migrantCount;
    private Long    estimateddeliveryMonthlySavingsRs;
    private Long    estimatedAnnualSavingsRs;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String aiReport;

    private LocalDateTime generatedAt;

    @PrePersist
    protected void onCreate() {
        if (generatedAt == null) generatedAt = LocalDateTime.now();
    }

    public AuditReport() {}

    // Getters
    public Long getId()                             { return id; }
    public Integer getTotalScanned()                { return totalScanned; }
    public Integer getGhostsFound()                 { return ghostsFound; }
    public Integer getVerifiedCount()               { return verifiedCount; }
    public Integer getMigrantCount()                { return migrantCount; }
    public Long getEstimateddeliveryMonthlySavingsRs()      { return estimateddeliveryMonthlySavingsRs; }
    public Long getEstimatedAnnualSavingsRs()       { return estimatedAnnualSavingsRs; }
    public String getAiReport()                     { return aiReport; }
    public LocalDateTime getGeneratedAt()           { return generatedAt; }

    // Setters
    public void setId(Long v)                              { this.id = v; }
    public void setTotalScanned(Integer v)                 { this.totalScanned = v; }
    public void setGhostsFound(Integer v)                  { this.ghostsFound = v; }
    public void setVerifiedCount(Integer v)                { this.verifiedCount = v; }
    public void setMigrantCount(Integer v)                 { this.migrantCount = v; }
    public void setEstimateddeliveryMonthlySavingsRs(Long v)       { this.estimateddeliveryMonthlySavingsRs = v; }
    public void setEstimatedAnnualSavingsRs(Long v)        { this.estimatedAnnualSavingsRs = v; }
    public void setAiReport(String v)                      { this.aiReport = v; }
    public void setGeneratedAt(LocalDateTime v)            { this.generatedAt = v; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final AuditReport r = new AuditReport();
        public Builder totalScanned(Integer v)              { r.totalScanned = v; return this; }
        public Builder ghostsFound(Integer v)               { r.ghostsFound = v; return this; }
        public Builder verifiedCount(Integer v)             { r.verifiedCount = v; return this; }
        public Builder migrantCount(Integer v)              { r.migrantCount = v; return this; }
        public Builder estimateddeliveryMonthlySavingsRs(Long v)    { r.estimateddeliveryMonthlySavingsRs = v; return this; }
        public Builder estimatedAnnualSavingsRs(Long v)     { r.estimatedAnnualSavingsRs = v; return this; }
        public Builder aiReport(String v)                   { r.aiReport = v; return this; }
        public AuditReport build()                          { return r; }
    }
}
