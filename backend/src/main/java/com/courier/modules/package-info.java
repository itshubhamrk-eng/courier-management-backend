/**
 * Business features, one package each, laid out per {@code MEMORY/ARCHITECTURE.md} §1:
 *
 * <pre>
 * modules/&lt;feature&gt;
 *   ├── api             controllers + DTOs
 *   ├── application     use cases, @Transactional, @PreAuthorize
 *   ├── domain          entities, value objects, repository interfaces
 *   └── infrastructure  Spring Data implementations, external adapters
 * </pre>
 *
 * Build order is {@code auth -> subscription -> company -> finance -> master ->
 * shipment}; each has a specification in {@code MEMORY/modules/}.
 *
 * <p>There is no {@code tenant} package. A company <em>is</em> the tenant, so
 * {@code modules/company} is the ownership root and {@code companyId} is the key
 * stamped on every row a company owns.
 */
package com.courier.modules;
