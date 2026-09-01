/**
 * Customer Management models — mirror the backend `com.courier.modules.customer` module
 * one-to-one (see MEMORY/modules/customer.md). A customer is reusable master data,
 * independent of Shipment Order; it can carry many addresses. No mock shapes: every
 * field is returned by, or accepted by, an endpoint.
 */

export type CustomerType = 'INDIVIDUAL' | 'BUSINESS';
export type CustomerStatus = 'ACTIVE' | 'INACTIVE';
export type AddressType = 'HOME' | 'OFFICE' | 'WAREHOUSE';

export const CUSTOMER_TYPES: CustomerType[] = ['INDIVIDUAL', 'BUSINESS'];
export const ADDRESS_TYPES: AddressType[] = ['HOME', 'OFFICE', 'WAREHOUSE'];

/** List projection — mirrors backend `CustomerSummaryResponse` (GET /customers). */
export interface Customer {
  id: string;
  customerCode: string;
  customerType: CustomerType;
  displayName: string;
  mobile: string;
  email?: string | null;
  status: CustomerStatus;
  createdDate?: string | null;
  version: number;
}

/** One address on a customer — mirrors backend `CustomerAddressResponse`. */
export interface CustomerAddress {
  id: string;
  companyId: string;
  customerId: string;
  addressType: AddressType;
  countryId?: string | null;
  stateId?: string | null;
  districtId?: string | null;
  cityId?: string | null;
  areaId?: string | null;
  pincodeId?: string | null;
  addressLine1: string;
  addressLine2?: string | null;
  landmark?: string | null;
  latitude?: number | null;
  longitude?: number | null;
  isDefaultPickup: boolean;
  isDefaultDelivery: boolean;
  status: CustomerStatus;
  createdBy?: string | null;
  createdDate?: string | null;
  updatedBy?: string | null;
  updatedDate?: string | null;
  version: number;
}

/** Full representation, with every address on file — mirrors backend `CustomerResponse`. */
export interface CustomerResponse {
  id: string;
  companyId: string;
  customerCode: string;
  customerType: CustomerType;
  companyName?: string | null;
  firstName: string;
  middleName?: string | null;
  lastName: string;
  displayName: string;
  mobile: string;
  alternateMobile?: string | null;
  email?: string | null;
  gstNumber?: string | null;
  panNumber?: string | null;
  /** Communication Center preferences — see MEMORY/modules/communication.md. Opt-out,
   *  not opt-in: a new customer starts with all three enabled. */
  whatsappEnabled: boolean;
  smsEnabled: boolean;
  emailEnabled: boolean;
  status: CustomerStatus;
  createdBy?: string | null;
  createdDate?: string | null;
  updatedBy?: string | null;
  updatedDate?: string | null;
  version: number;
  addresses: CustomerAddress[];
}

/**
 * Body of POST /customers — mirrors backend `CreateCustomerRequest`. `customerCode` is
 * optional; blank has the backend generate one. `status` is not accepted (a new customer
 * starts ACTIVE). GST is mandatory only when `customerType` is `BUSINESS` — enforced
 * server-side because it depends on another field.
 */
export interface CreateCustomerRequest {
  customerCode?: string | null;
  customerType: CustomerType;
  companyName?: string | null;
  firstName: string;
  middleName?: string | null;
  lastName: string;
  mobile: string;
  alternateMobile?: string | null;
  email?: string | null;
  gstNumber?: string | null;
  panNumber?: string | null;
  /** Defaults to true server-side when omitted. */
  whatsappEnabled?: boolean;
  smsEnabled?: boolean;
  emailEnabled?: boolean;
}

/**
 * Body of PUT /customers/{id} — mirrors backend `UpdateCustomerRequest`. Full replacement
 * of the editable fields; carries `version`. `customerCode` is immutable and omitted;
 * `status` has its own lifecycle endpoints.
 */
export interface UpdateCustomerRequest {
  customerType: CustomerType;
  companyName?: string | null;
  firstName: string;
  middleName?: string | null;
  lastName: string;
  mobile: string;
  alternateMobile?: string | null;
  email?: string | null;
  gstNumber?: string | null;
  panNumber?: string | null;
  whatsappEnabled: boolean;
  smsEnabled: boolean;
  emailEnabled: boolean;
  version: number;
}

/** Advanced-filter criteria for GET /customers. All optional; merged into the page query. */
export interface CustomerSearchRequest {
  customerType?: CustomerType[];
  status?: CustomerStatus[];
  search?: string;
}

/** Body of POST /customers/{id}/addresses — mirrors backend `CreateCustomerAddressRequest`. */
export interface CreateCustomerAddressRequest {
  addressType: AddressType;
  countryId?: string | null;
  stateId?: string | null;
  districtId?: string | null;
  cityId?: string | null;
  areaId?: string | null;
  pincodeId?: string | null;
  addressLine1: string;
  addressLine2?: string | null;
  landmark?: string | null;
  latitude?: number | null;
  longitude?: number | null;
  isDefaultPickup?: boolean;
  isDefaultDelivery?: boolean;
}

/**
 * Body of PUT /customers/{id}/addresses/{addressId}. Full replacement; carries `version`.
 * Mirrors backend `UpdateCustomerAddressRequest`.
 */
export interface UpdateCustomerAddressRequest extends CreateCustomerAddressRequest {
  version: number;
}
