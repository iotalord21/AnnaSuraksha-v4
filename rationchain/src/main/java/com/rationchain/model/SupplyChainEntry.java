package com.rationchain.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "supply_chain_entries")
public class SupplyChainEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private Stage   stage;
    private String  shipmentId;
    private String  warehouseId;
    private String  fpsShopId;
    private String  stateCode;
    private String  district;
    private Integer dispatchedRiceKg;
    private Integer dispatchedWheatKg;
    private Integer dispatchedSugarKg;
    private Integer receivedRiceKg;
    private Integer receivedWheatKg;
    private Integer receivedSugarKg;
    private Integer riceDiscrepancyKg;
    private Integer wheatDiscrepancyKg;
    private Integer sugarDiscrepancyKg;
    private Boolean discrepancyFlagged;
    private String  discrepancyReason;
    private String  warehouseOfficerId;
    private String  transporterId;
    private String  fpsOperatorId;
    private String  entryHash;
    private String  prevEntryHash;
    private Long    entryHeight;
    private LocalDateTime dispatchedAt;
    private LocalDateTime receivedAt;
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt          == null) createdAt          = LocalDateTime.now();
        if (discrepancyFlagged == null) discrepancyFlagged = false;
    }

    public SupplyChainEntry() {}

    public enum Stage {
        WAREHOUSE_LOADED, DISPATCHED, IN_TRANSIT, FPS_RECEIVED, DISTRIBUTED, CONFIRMED
    }

    // Getters
    public Long getId()                          { return id; }
    public Stage getStage()                      { return stage; }
    public String getShipmentId()                { return shipmentId; }
    public String getWarehouseId()               { return warehouseId; }
    public String getFpsShopId()                 { return fpsShopId; }
    public String getStateCode()                 { return stateCode; }
    public String getDistrict()                  { return district; }
    public Integer getDispatchedRiceKg()         { return dispatchedRiceKg; }
    public Integer getDispatchedWheatKg()        { return dispatchedWheatKg; }
    public Integer getDispatchedSugarKg()        { return dispatchedSugarKg; }
    public Integer getReceivedRiceKg()           { return receivedRiceKg; }
    public Integer getReceivedWheatKg()          { return receivedWheatKg; }
    public Integer getReceivedSugarKg()          { return receivedSugarKg; }
    public Integer getRiceDiscrepancyKg()        { return riceDiscrepancyKg; }
    public Integer getWheatDiscrepancyKg()       { return wheatDiscrepancyKg; }
    public Integer getSugarDiscrepancyKg()       { return sugarDiscrepancyKg; }
    public Boolean getDiscrepancyFlagged()       { return discrepancyFlagged; }
    public String getDiscrepancyReason()         { return discrepancyReason; }
    public String getWarehouseOfficerId()        { return warehouseOfficerId; }
    public String getTransporterId()             { return transporterId; }
    public String getFpsOperatorId()             { return fpsOperatorId; }
    public String getEntryHash()                 { return entryHash; }
    public String getPrevEntryHash()             { return prevEntryHash; }
    public Long getEntryHeight()                 { return entryHeight; }
    public LocalDateTime getDispatchedAt()       { return dispatchedAt; }
    public LocalDateTime getReceivedAt()         { return receivedAt; }
    public LocalDateTime getCreatedAt()          { return createdAt; }

    // Setters
    public void setId(Long v)                          { this.id = v; }
    public void setStage(Stage v)                      { this.stage = v; }
    public void setShipmentId(String v)                { this.shipmentId = v; }
    public void setWarehouseId(String v)               { this.warehouseId = v; }
    public void setFpsShopId(String v)                 { this.fpsShopId = v; }
    public void setStateCode(String v)                 { this.stateCode = v; }
    public void setDistrict(String v)                  { this.district = v; }
    public void setDispatchedRiceKg(Integer v)         { this.dispatchedRiceKg = v; }
    public void setDispatchedWheatKg(Integer v)        { this.dispatchedWheatKg = v; }
    public void setDispatchedSugarKg(Integer v)        { this.dispatchedSugarKg = v; }
    public void setReceivedRiceKg(Integer v)           { this.receivedRiceKg = v; }
    public void setReceivedWheatKg(Integer v)          { this.receivedWheatKg = v; }
    public void setReceivedSugarKg(Integer v)          { this.receivedSugarKg = v; }
    public void setRiceDiscrepancyKg(Integer v)        { this.riceDiscrepancyKg = v; }
    public void setWheatDiscrepancyKg(Integer v)       { this.wheatDiscrepancyKg = v; }
    public void setSugarDiscrepancyKg(Integer v)       { this.sugarDiscrepancyKg = v; }
    public void setDiscrepancyFlagged(Boolean v)       { this.discrepancyFlagged = v; }
    public void setDiscrepancyReason(String v)         { this.discrepancyReason = v; }
    public void setWarehouseOfficerId(String v)        { this.warehouseOfficerId = v; }
    public void setTransporterId(String v)             { this.transporterId = v; }
    public void setFpsOperatorId(String v)             { this.fpsOperatorId = v; }
    public void setEntryHash(String v)                 { this.entryHash = v; }
    public void setPrevEntryHash(String v)             { this.prevEntryHash = v; }
    public void setEntryHeight(Long v)                 { this.entryHeight = v; }
    public void setDispatchedAt(LocalDateTime v)       { this.dispatchedAt = v; }
    public void setReceivedAt(LocalDateTime v)         { this.receivedAt = v; }
    public void setCreatedAt(LocalDateTime v)          { this.createdAt = v; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final SupplyChainEntry e = new SupplyChainEntry();
        public Builder stage(Stage v)                    { e.stage = v; return this; }
        public Builder shipmentId(String v)              { e.shipmentId = v; return this; }
        public Builder warehouseId(String v)             { e.warehouseId = v; return this; }
        public Builder fpsShopId(String v)               { e.fpsShopId = v; return this; }
        public Builder stateCode(String v)               { e.stateCode = v; return this; }
        public Builder district(String v)                { e.district = v; return this; }
        public Builder dispatchedRiceKg(Integer v)       { e.dispatchedRiceKg = v; return this; }
        public Builder dispatchedWheatKg(Integer v)      { e.dispatchedWheatKg = v; return this; }
        public Builder dispatchedSugarKg(Integer v)      { e.dispatchedSugarKg = v; return this; }
        public Builder receivedRiceKg(Integer v)         { e.receivedRiceKg = v; return this; }
        public Builder receivedWheatKg(Integer v)        { e.receivedWheatKg = v; return this; }
        public Builder receivedSugarKg(Integer v)        { e.receivedSugarKg = v; return this; }
        public Builder riceDiscrepancyKg(Integer v)      { e.riceDiscrepancyKg = v; return this; }
        public Builder wheatDiscrepancyKg(Integer v)     { e.wheatDiscrepancyKg = v; return this; }
        public Builder sugarDiscrepancyKg(Integer v)     { e.sugarDiscrepancyKg = v; return this; }
        public Builder discrepancyFlagged(Boolean v)     { e.discrepancyFlagged = v; return this; }
        public Builder discrepancyReason(String v)       { e.discrepancyReason = v; return this; }
        public Builder warehouseOfficerId(String v)      { e.warehouseOfficerId = v; return this; }
        public Builder transporterId(String v)           { e.transporterId = v; return this; }
        public Builder fpsOperatorId(String v)           { e.fpsOperatorId = v; return this; }
        public Builder prevEntryHash(String v)           { e.prevEntryHash = v; return this; }
        public Builder entryHeight(Long v)               { e.entryHeight = v; return this; }
        public Builder dispatchedAt(LocalDateTime v)     { e.dispatchedAt = v; return this; }
        public Builder receivedAt(LocalDateTime v)       { e.receivedAt = v; return this; }
        public SupplyChainEntry build()                  { return e; }
    }
}
