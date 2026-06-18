package com.immobilier.backend.dto;

public class AIPriceResponse {

    private long   estimatedPrice;
    private int    confidence;
    private long   minPrice;
    private long   maxPrice;
    private String marketDemand;
    private long   pricePerM2;
    private String modelVersion;

    public AIPriceResponse() {}

    public long   getEstimatedPrice()            { return estimatedPrice; }
    public void   setEstimatedPrice(long v)      { this.estimatedPrice = v; }

    public int    getConfidence()                { return confidence; }
    public void   setConfidence(int v)           { this.confidence = v; }

    public long   getMinPrice()                  { return minPrice; }
    public void   setMinPrice(long v)            { this.minPrice = v; }

    public long   getMaxPrice()                  { return maxPrice; }
    public void   setMaxPrice(long v)            { this.maxPrice = v; }

    public String getMarketDemand()              { return marketDemand; }
    public void   setMarketDemand(String v)      { this.marketDemand = v; }

    public long   getPricePerM2()                { return pricePerM2; }
    public void   setPricePerM2(long v)          { this.pricePerM2 = v; }

    public String getModelVersion()              { return modelVersion; }
    public void   setModelVersion(String v)      { this.modelVersion = v; }
}
