package com.hylir.receipt.service.autocapture;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 条码去重器（内存级）
 */
public class BarcodeDeduplicator {

    private final Set<String> processed = ConcurrentHashMap.newKeySet();

    public boolean isDuplicate(String barcode) {
        if (barcode == null || barcode.isEmpty()) return false;
        return !processed.add(barcode);
    }

    public int getProcessedCount() {
        return processed.size();
    }

    public void clear() {
        processed.clear();
    }
}