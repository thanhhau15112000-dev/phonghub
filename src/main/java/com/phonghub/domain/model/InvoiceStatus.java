package com.phonghub.domain.model;

/**
 * Mirrors the CHECK constraint of the invoices table (V1).
 * The application issues ISSUED invoices and moves them to PARTIALLY_PAID / PAID;
 * OVERDUE is derived from the due date at read time and is not persisted by the app.
 */
public enum InvoiceStatus {
    DRAFT,
    ISSUED,
    PARTIALLY_PAID,
    PAID,
    OVERDUE,
    VOIDED
}
