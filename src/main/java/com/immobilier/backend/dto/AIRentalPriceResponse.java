package com.immobilier.backend.dto;

public class AIRentalPriceResponse {

    private long   estimatedMonthlyRent;
    private int    confidence;
    private long   minRent;
    private long   maxRent;
    private String marketDemand;
    private long   pricePerM2;
    private String modelVersion;

    public AIRentalPriceResponse() {}

    public long   getEstimatedMonthlyRent()           { return estimatedMonthlyRent; }
    public void   setEstimatedMonthlyRent(long v)     { this.estimatedMonthlyRent = v; }

    public int    getConfidence()                     { return confidence; }
    public void   setConfidence(int v)                { this.confidence = v; }

    public long   getMinRent()                        { return minRent; }
    public void   setMinRent(long v)                  { this.minRent = v; }

    public long   getMaxRent()                        { return maxRent; }
    public void   setMaxRent(long v)                  { this.maxRent = v; }

    public String getMarketDemand()                   { return marketDemand; }
    public void   setMarketDemand(String v)           { this.marketDemand = v; }

    public long   getPricePerM2()                     { return pricePerM2; }
    public void   setPricePerM2(long v)               { this.pricePerM2 = v; }

    public String getModelVersion()                   { return modelVersion; }
    public void   setModelVersion(String v)           { this.modelVersion = v; }
}
