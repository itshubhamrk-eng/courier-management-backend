package com.courier.modules.company.application;

import java.util.List;
import java.util.UUID;

/** Which sidebar menus a SUPER_ADMIN has hidden from a company. */
public interface CompanyMenuLockService {

    /** Locked menu codes of one company, sorted. SUPER_ADMIN only. */
    List<String> lockedMenus(UUID companyId);

    /** Locked menu codes of the caller's own company; empty when tenant-unbound. */
    List<String> lockedMenusOfCurrentCompany();

    /** Replaces the company's whole lock set. SUPER_ADMIN only. */
    List<String> replaceLockedMenus(UUID companyId, List<String> menuCodes);
}
