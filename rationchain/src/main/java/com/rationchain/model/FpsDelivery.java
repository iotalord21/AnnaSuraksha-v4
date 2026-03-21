package com.rationchain.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "fps_deliveries")
public class FpsDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long beneficiaryId;
    private String beneficiaryName;
    private String fpsShopId;
    private String fpsOperatorName;
    private String stateCode;
    private String deliveryMonth;
    private Integer dealerRiceKg;
    private Integer dealerWheatKg;
    private Integer dealerSugarKg;
    private Integer confirmedRiceKg;
    private Integer confirmedWheatKg;
    private Integer confirmedSugarKg;
    private String txHash;
    private Long blockHeight;
    private String dealerStatus;
    private String beneficiaryStatus;
    private Boolean flagged;
    private String flagReason;
    private LocalDateTime dealerConfirmedAt;
    private LocalDateTime beneficiaryConfirmedAt;

    @PrePersist
    protected void onCreate() {
        if (beneficiaryStatus == null)
            beneficiaryStatus = "PENDING";
        if (flagged == null)
            flagged = false;
    }

    public FpsDelivery() {
    }

    // Getters
    public Long getId() {
        return id;
    }

    public Long getBeneficiaryId() {
        return beneficiaryId;
    }

    public String getBeneficiaryName() {
        return beneficiaryName;
    }

    public String getFpsShopId() {
        return fpsShopId;
    }

    public String getFpsOperatorName() {
        return fpsOperatorName;
    }

    public String getStateCode() {
        return stateCode;
    }

    public String getDeliveryMonth() {
        return deliveryMonth;
    }

    public Integer getDealerRiceKg() {
        return dealerRiceKg;
    }

    public Integer getDealerWheatKg() {
        return dealerWheatKg;
    }

    public Integer getDealerSugarKg() {
        return dealerSugarKg;
    }

    public Integer getConfirmedRiceKg() {
        return confirmedRiceKg;
    }

    public Integer getConfirmedWheatKg() {
        return confirmedWheatKg;
    }

    public Integer getConfirmedSugarKg() {
        return confirmedSugarKg;
    }

    public String getTxHash() {
        return txHash;
    }

    public Long getBlockHeight() {
        return blockHeight;
    }

    public String getDealerStatus() {
        return dealerStatus;
    }

    public String getBeneficiaryStatus() {
        return beneficiaryStatus;
    }

    public Boolean getFlagged() {
        return flagged;
    }

    public String getFlagReason() {
        return flagReason;
    }

    public LocalDateTime getDealerConfirmedAt() {
        return dealerConfirmedAt;
    }

    public LocalDateTime getBeneficiaryConfirmedAt() {
        return beneficiaryConfirmedAt;
    }

    // Setters
    public void setId(Long v) {
        this.id = v;
    }

    public void setBeneficiaryId(Long v) {
        this.beneficiaryId = v;
    }

    public void setBeneficiaryName(String v) {
        this.beneficiaryName = v;
    }

    public void setFpsShopId(String v) {
        this.fpsShopId = v;
    }

    public void setFpsOperatorName(String v) {
        this.fpsOperatorName = v;
    }

    public void setStateCode(String v) {
        this.stateCode = v;
    }

    public void setDeliveryMonth(String v) {
        this.deliveryMonth = v;
    }

    public void setDealerRiceKg(Integer v) {
        this.dealerRiceKg = v;
    }

    public void setDealerWheatKg(Integer v) {
        this.dealerWheatKg = v;
    }

    public void setDealerSugarKg(Integer v) {
        this.dealerSugarKg = v;
    }

    public void setConfirmedRiceKg(Integer v) {
        this.confirmedRiceKg = v;
    }

    public void setConfirmedWheatKg(Integer v) {
        this.confirmedWheatKg = v;
    }

    public void setConfirmedSugarKg(Integer v) {
        this.confirmedSugarKg = v;
    }

    public void setTxHash(String v) {
        this.txHash = v;
    }

    public void setBlockHeight(Long v) {
        this.blockHeight = v;
    }

    public void setDealerStatus(String v) {
        this.dealerStatus = v;
    }

    public void setBeneficiaryStatus(String v) {
        this.beneficiaryStatus = v;
    }

    public void setFlagged(Boolean v) {
        this.flagged = v;
    }

    public void setFlagReason(String v) {
        this.flagReason = v;
    }

    public void setDealerConfirmedAt(LocalDateTime v) {
        this.dealerConfirmedAt = v;
    }

    public void setBeneficiaryConfirmedAt(LocalDateTime v) {
        this.beneficiaryConfirmedAt = v;
    }

    public static FpsDeliveryBuilder builder() {
        return new FpsDeliveryBuilder();
    }

    public static class FpsDeliveryBuilder {
        private final FpsDelivery d = new FpsDelivery();

        public FpsDeliveryBuilder beneficiaryId(Long v) {
            d.beneficiaryId = v;
            return this;
        }

        public FpsDeliveryBuilder beneficiaryName(String v) {
            d.beneficiaryName = v;
            return this;
        }

        public FpsDeliveryBuilder fpsShopId(String v) {
            d.fpsShopId = v;
            return this;
        }

        public FpsDeliveryBuilder fpsOperatorName(String v) {
            d.fpsOperatorName = v;
            return this;
        }

        public FpsDeliveryBuilder stateCode(String v) {
            d.stateCode = v;
            return this;
        }

        public FpsDeliveryBuilder deliveryMonth(String v) {
            d.deliveryMonth = v;
            return this;
        }

        public FpsDeliveryBuilder dealerRiceKg(Integer v) {
            d.dealerRiceKg = v;
            return this;
        }

        public FpsDeliveryBuilder dealerWheatKg(Integer v) {
            d.dealerWheatKg = v;
            return this;
        }

        public FpsDeliveryBuilder dealerSugarKg(Integer v) {
            d.dealerSugarKg = v;
            return this;
        }

        public FpsDeliveryBuilder confirmedRiceKg(Integer v) {
            d.confirmedRiceKg = v;
            return this;
        }

        public FpsDeliveryBuilder confirmedWheatKg(Integer v) {
            d.confirmedWheatKg = v;
            return this;
        }

        public FpsDeliveryBuilder confirmedSugarKg(Integer v) {
            d.confirmedSugarKg = v;
            return this;
        }

        public FpsDeliveryBuilder txHash(String v) {
            d.txHash = v;
            return this;
        }

        public FpsDeliveryBuilder blockHeight(Long v) {
            d.blockHeight = v;
            return this;
        }

        public FpsDeliveryBuilder dealerStatus(String v) {
            d.dealerStatus = v;
            return this;
        }

        public FpsDeliveryBuilder beneficiaryStatus(String v) {
            d.beneficiaryStatus = v;
            return this;
        }

        public FpsDeliveryBuilder flagged(Boolean v) {
            d.flagged = v;
            return this;
        }

        public FpsDeliveryBuilder flagReason(String v) {
            d.flagReason = v;
            return this;
        }

        public FpsDeliveryBuilder dealerConfirmedAt(LocalDateTime v) {
            d.dealerConfirmedAt = v;
            return this;
        }

        public FpsDelivery build() {
            return d;
        }
    }
}
