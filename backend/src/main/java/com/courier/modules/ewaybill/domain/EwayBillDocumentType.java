package com.courier.modules.ewaybill.domain;

/** The document an E-Way Bill is raised against — the standard GST e-way bill vocabulary. */
public enum EwayBillDocumentType {
    INVOICE,
    BILL_OF_SUPPLY,
    DELIVERY_CHALLAN,
    OTHERS
}
