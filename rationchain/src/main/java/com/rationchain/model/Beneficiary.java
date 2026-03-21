package com.rationchain.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "beneficiaries")
public class Beneficiary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String aadhaarHash;
    private String voterIdHash;
    private String maskedAadhaar;
    private String fullName;
    private String stateCode;
    private String stateName;
    private String district;
    private String category;

    @Column(nullable = false)
    private Integer familySize;

    private String  phone;
    private Integer riceKg;
    private Integer wheatKg;
    private Integer sugarKg;
    private Integer keroseneL;
    private String  fpsShopId;
    private String  status;
    private Boolean migrant;
    private String  claimState;
    private Integer claimCount;
    private String  blockHash;
    private String  prevBlockHash;
    private Long    blockHeight;
    private String  ghostReason;
    private String  ghostLayer;
    private LocalDateTime registeredAt;
    private LocalDateTime lastClaimAt;
    private LocalDateTime flaggedAt;

    @PrePersist
    protected void onCreate() {
        if (registeredAt == null) registeredAt = LocalDateTime.now();
        if (claimCount   == null) claimCount   = 0;
        if (migrant      == null) migrant       = false;
        if (status       == null) status        = "ACTIVE";
    }

    // ── Constructors ──────────────────────────────────────────────────────
    public Beneficiary() {}

    // ── Getters ───────────────────────────────────────────────────────────
    public Long getId()                    { return id; }
    public String getAadhaarHash()         { return aadhaarHash; }
    public String getVoterIdHash()         { return voterIdHash; }
    public String getMaskedAadhaar()       { return maskedAadhaar; }
    public String getFullName()            { return fullName; }
    public String getStateCode()           { return stateCode; }
    public String getStateName()           { return stateName; }
    public String getDistrict()            { return district; }
    public String getCategory()            { return category; }
    public Integer getFamilySize()         { return familySize; }
    public String getPhone()               { return phone; }
    public Integer getRiceKg()             { return riceKg; }
    public Integer getWheatKg()            { return wheatKg; }
    public Integer getSugarKg()            { return sugarKg; }
    public Integer getKeroseneL()          { return keroseneL; }
    public String getFpsShopId()           { return fpsShopId; }
    public String getStatus()              { return status; }
    public Boolean getMigrant()            { return migrant; }
    public String getClaimState()          { return claimState; }
    public Integer getClaimCount()         { return claimCount; }
    public String getBlockHash()           { return blockHash; }
    public String getPrevBlockHash()       { return prevBlockHash; }
    public Long getBlockHeight()           { return blockHeight; }
    public String getGhostReason()         { return ghostReason; }
    public String getGhostLayer()          { return ghostLayer; }
    public LocalDateTime getRegisteredAt() { return registeredAt; }
    public LocalDateTime getLastClaimAt()  { return lastClaimAt; }
    public LocalDateTime getFlaggedAt()    { return flaggedAt; }

    // ── Setters ───────────────────────────────────────────────────────────
    public void setId(Long id)                           { this.id = id; }
    public void setAadhaarHash(String v)                 { this.aadhaarHash = v; }
    public void setVoterIdHash(String v)                 { this.voterIdHash = v; }
    public void setMaskedAadhaar(String v)               { this.maskedAadhaar = v; }
    public void setFullName(String v)                    { this.fullName = v; }
    public void setStateCode(String v)                   { this.stateCode = v; }
    public void setStateName(String v)                   { this.stateName = v; }
    public void setDistrict(String v)                    { this.district = v; }
    public void setCategory(String v)                    { this.category = v; }
    public void setFamilySize(Integer v)                 { this.familySize = v; }
    public void setPhone(String v)                       { this.phone = v; }
    public void setRiceKg(Integer v)                     { this.riceKg = v; }
    public void setWheatKg(Integer v)                    { this.wheatKg = v; }
    public void setSugarKg(Integer v)                    { this.sugarKg = v; }
    public void setKeroseneL(Integer v)                  { this.keroseneL = v; }
    public void setFpsShopId(String v)                   { this.fpsShopId = v; }
    public void setStatus(String v)                      { this.status = v; }
    public void setMigrant(Boolean v)                    { this.migrant = v; }
    public void setClaimState(String v)                  { this.claimState = v; }
    public void setClaimCount(Integer v)                 { this.claimCount = v; }
    public void setBlockHash(String v)                   { this.blockHash = v; }
    public void setPrevBlockHash(String v)               { this.prevBlockHash = v; }
    public void setBlockHeight(Long v)                   { this.blockHeight = v; }
    public void setGhostReason(String v)                 { this.ghostReason = v; }
    public void setGhostLayer(String v)                  { this.ghostLayer = v; }
    public void setRegisteredAt(LocalDateTime v)         { this.registeredAt = v; }
    public void setLastClaimAt(LocalDateTime v)          { this.lastClaimAt = v; }
    public void setFlaggedAt(LocalDateTime v)            { this.flaggedAt = v; }

    // ── Builder ───────────────────────────────────────────────────────────
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final Beneficiary b = new Beneficiary();
        public Builder id(Long v)                    { b.id = v; return this; }
        public Builder aadhaarHash(String v)         { b.aadhaarHash = v; return this; }
        public Builder voterIdHash(String v)         { b.voterIdHash = v; return this; }
        public Builder maskedAadhaar(String v)       { b.maskedAadhaar = v; return this; }
        public Builder fullName(String v)            { b.fullName = v; return this; }
        public Builder stateCode(String v)           { b.stateCode = v; return this; }
        public Builder stateName(String v)           { b.stateName = v; return this; }
        public Builder district(String v)            { b.district = v; return this; }
        public Builder category(String v)            { b.category = v; return this; }
        public Builder familySize(Integer v)         { b.familySize = v; return this; }
        public Builder phone(String v)               { b.phone = v; return this; }
        public Builder riceKg(Integer v)             { b.riceKg = v; return this; }
        public Builder wheatKg(Integer v)            { b.wheatKg = v; return this; }
        public Builder sugarKg(Integer v)            { b.sugarKg = v; return this; }
        public Builder keroseneL(Integer v)          { b.keroseneL = v; return this; }
        public Builder fpsShopId(String v)           { b.fpsShopId = v; return this; }
        public Builder status(String v)              { b.status = v; return this; }
        public Builder migrant(Boolean v)            { b.migrant = v; return this; }
        public Builder claimState(String v)          { b.claimState = v; return this; }
        public Builder claimCount(Integer v)         { b.claimCount = v; return this; }
        public Builder blockHash(String v)           { b.blockHash = v; return this; }
        public Builder prevBlockHash(String v)       { b.prevBlockHash = v; return this; }
        public Builder blockHeight(Long v)           { b.blockHeight = v; return this; }
        public Builder ghostReason(String v)         { b.ghostReason = v; return this; }
        public Builder ghostLayer(String v)          { b.ghostLayer = v; return this; }
        public Builder flaggedAt(LocalDateTime v)    { b.flaggedAt = v; return this; }
        public Builder registeredAt(LocalDateTime v) { b.registeredAt = v; return this; }
        public Builder lastClaimAt(LocalDateTime v)  { b.lastClaimAt = v; return this; }
        public Beneficiary build()                   { return b; }
    }
}
