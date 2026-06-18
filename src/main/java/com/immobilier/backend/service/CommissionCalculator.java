package com.immobilier.backend.service;

import org.springframework.stereotype.Service;

/**
 * Single source of truth for commission amount math.
 * Used by every path that records a commission so the formula is never duplicated.
 */
@Service
public class CommissionCalculator {

    /**
     * @param basePrice the transaction price (sale or rental amount)
     * @param rate      percentage (when type=PERCENTAGE) or flat amount (when type=FIXED)
     * @param type      "PERCENTAGE" (default) or "FIXED"
     * @return the commission amount, never negative, rounded to 2 decimals
     */
    public double compute(double basePrice, double rate, String type) {
        if (rate <= 0) return 0.0;
        if ("FIXED".equalsIgnoreCase(type)) {
            return round2(rate);
        }
        if (basePrice <= 0) return 0.0;
        return round2(basePrice * rate / 100.0);
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
