package com.rationchain.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "claim_records")
public class ClaimRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long    beneficiaryId;
    private String  beneficiaryName;
    private String  aadhaarHash;
    private String  stateCode;
    private String  claimStateCode;
    private String  fpsShopId;
    private String  category;
    private Integer riceKg;
    private Integer wheatKg;
    private Integer sugarKg;
    private String  txHash;
    private Long    blockHeight;
    private String  biometricStatus;
    private LocalDateTime claimedAt;

    @PrePersist
    protected void onCreate() {
        if (claimedAt == null) claimedAt = LocalDateTime.now();
    }

    public ClaimRecord() {}

    // Getters
    public Long getId()                  { return id; }
    public Long getBeneficiaryId()       { return beneficiaryId; }
    public String getBeneficiaryName()   { return beneficiaryName; }
    public String getAadhaarHash()       { return aadhaarHash; }
    public String getStateCode()         { return stateCode; }
    public String getClaimStateCode()    { return claimStateCode; }
    public String getFpsShopId()         { return fpsShopId; }
    public String getCategory()          { return category; }
    public Integer getRiceKg()           { return riceKg; }
    public Integer getWheatKg()          { return wheatKg; }
    public Integer getSugarKg()          { return sugarKg; }
    public String getTxHash()            { return txHash; }
    public Long getBlockHeight()         { return blockHeight; }
    public String getBiometricStatus()   { return biometricStatus; }
    public LocalDateTime getClaimedAt()  { return claimedAt; }

    // Setters
    public void setId(Long v)                    { this.id = v; }
    public void setBeneficiaryId(Long v)         { this.beneficiaryId = v; }
    public void setBeneficiaryName(String v)     { this.beneficiaryName = v; }
    public void setAadhaarHash(String v)         { this.aadhaarHash = v; }
    public void setStateCode(String v)           { this.stateCode = v; }
    public void setClaimStateCode(String v)      { this.claimStateCode = v; }
    public void setFpsShopId(String v)           { this.fpsShopId = v; }
    public void setCategory(String v)            { this.category = v; }
    public void setRiceKg(Integer v)             { this.riceKg = v; }
    public void setWheatKg(Integer v)            { this.wheatKg = v; }
    public void setSugarKg(Integer v)            { this.sugarKg = v; }
    public void setTxHash(String v)              { this.txHash = v; }
    public void setBlockHeight(Long v)           { this.blockHeight = v; }
    public void setBiometricStatus(String v)     { this.biometricStatus = v; }
    public void setClaimedAt(LocalDateTime v)    { this.claimedAt = v; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final ClaimRecord c = new ClaimRecord();
        public Builder beneficiaryId(Long v)       { c.beneficiaryId = v; return this; }
        public Builder beneficiaryName(String v)   { c.beneficiaryName = v; return this; }
        public Builder aadhaarHash(String v)       { c.aadhaarHash = v; return this; }
        public Builder stateCode(String v)         { c.stateCode = v; return this; }
        public Builder claimStateCode(String v)    { c.claimStateCode = v; return this; }
        public Builder fpsShopId(String v)         { c.fpsShopId = v; return this; }
        public Builder category(String v)          { c.category = v; return this; }
        public Builder riceKg(Integer v)           { c.riceKg = v; return this; }
        public Builder wheatKg(Integer v)          { c.wheatKg = v; return this; }
        public Builder sugarKg(Integer v)          { c.sugarKg = v; return this; }
        public Builder txHash(String v)            { c.txHash = v; return this; }
        public Builder blockHeight(Long v)         { c.blockHeight = v; return this; }
        public Builder biometricStatus(String v)   { c.biometricStatus = v; return this; }
        public ClaimRecord build()                 { return c; }
    }
}
