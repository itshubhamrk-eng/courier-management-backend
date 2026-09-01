/**
 * Branch Wallet models — one prepaid wallet per branch (created with the branch). Every
 * shape here maps one-to-one to the backend's `/branch-wallet` resource; no mock fields.
 * Money is a plain `number` in the wallet's `currency` (rupees, not paise) except
 * `RechargeOrderResponse.amountMinor`, which is the gateway's paise contract.
 *
 * There is no wallet-id-addressable collection on the backend by design (see
 * `BranchWalletController`): a caller gets their own branch's wallet, or a
 * `COMPANY_ADMIN` selects another via a `branchId` query parameter. Nothing here is
 * reached by a wallet id in a URL.
 */

export type WalletStatus = 'ACTIVE' | 'INACTIVE' | 'SUSPENDED' | 'CLOSED';

/** CR (credit) or DR (debit) — the wire form is the accounting code, not the English word. */
export type TransactionType = 'CR' | 'DR';

/** Gateway payment state behind a ledger entry. Null on entries that are not a payment. */
export type PaymentStatus = 'PENDING' | 'SUCCESS' | 'FAILED' | 'REFUNDED';

/** Why a transaction happened. The stored form is the three-letter code, shared by both
 *  directions as the backend does — see `SubTransactionType.Direction`. */
export type SubTransactionType =
  | 'WRC' | 'SBK' | 'SRF' | 'COD' | 'COM' | 'BST' | 'MCR' | 'MDB' | 'TRI' | 'TRO' | 'ADJ' | 'PNL';

export const SUB_TRANSACTION_LABELS: Record<SubTransactionType, string> = {
  WRC: 'Wallet Recharge', SBK: 'Shipment Booking', SRF: 'Shipment Refund', COD: 'COD Settlement',
  COM: 'Commission', BST: 'Branch Settlement', MCR: 'Manual Credit', MDB: 'Manual Debit',
  TRI: 'Transfer In', TRO: 'Transfer Out', ADJ: 'Adjustment', PNL: 'Penalty'
};

/** Reasons that may be filed on a credit — matches `SubTransactionType.creditable()`. */
export const CREDIT_SUB_TYPES: SubTransactionType[] = ['WRC', 'SRF', 'COD', 'COM', 'BST', 'MCR', 'TRI', 'ADJ'];
/** Reasons that may be filed on a debit — matches `SubTransactionType.debitable()`. */
export const DEBIT_SUB_TYPES: SubTransactionType[] = ['SBK', 'COD', 'COM', 'BST', 'MDB', 'TRO', 'ADJ', 'PNL'];
export const TRANSACTION_SUB_TYPES: SubTransactionType[] =
  ['WRC', 'SBK', 'SRF', 'COD', 'COM', 'BST', 'MCR', 'MDB', 'TRI', 'TRO', 'ADJ', 'PNL'];

/** What `referenceId` on a ledger entry points at. */
export type ReferenceType = 'PAYMENT' | 'SHIPMENT' | 'SETTLEMENT' | 'SYSTEM' | 'MANUAL';

/**
 * A branch wallet in full — mirrors backend `WalletResponse` (`GET /branch-wallet`).
 * `totalBalance` is server-derived (`available + hold`); never recompute it client-side.
 */
export interface WalletResponse {
  id: string;
  companyId: string;
  walletNumber: string;
  branchId: string;
  branchCode?: string | null;
  branchName?: string | null;
  status: WalletStatus;
  availableBalance: number;
  holdBalance: number;
  totalBalance: number;
  currency: string;
  createdBy?: string | null;
  createdAt: string;
  updatedBy?: string | null;
  updatedAt: string;
  version: number;
}

/**
 * The wallet dashboard in one response — mirrors backend `WalletSummaryResponse`
 * (`GET /branch-wallet/summary`). Every period figure is settled-only, computed server-side.
 */
export interface WalletSummary {
  walletId: string;
  walletNumber: string;
  branchId: string;
  branchCode?: string | null;
  branchName?: string | null;
  status: WalletStatus;
  currency: string;
  availableBalance: number;
  holdBalance: number;
  totalBalance: number;
  todayCredit: number;
  todayDebit: number;
  monthCredit: number;
  monthDebit: number;
  totalCredit: number;
  totalDebit: number;
  transactionCount: number;
  lastTransactionAt?: string | null;
  lastRechargeAmount?: number | null;
  lastRechargeAt?: string | null;
}

/** A ledger entry — mirrors backend `WalletTransactionResponse`. `balanceBefore` /
 *  `balanceAfter` are the wallet's available balance around this entry, as recorded at
 *  the time; they are facts, not recomputations. */
export interface WalletTransaction {
  id: string;
  companyId: string;
  walletId: string;
  transactionNo: string;
  transactionType: TransactionType;
  transactionTypeLabel: string;
  subTransactionType: SubTransactionType;
  subTransactionTypeLabel: string;
  amount: number;
  balanceBefore: number;
  balanceAfter: number;
  referenceType?: ReferenceType | null;
  referenceId?: string | null;
  remarks?: string | null;
  paymentGateway?: string | null;
  paymentReference?: string | null;
  paymentStatus?: PaymentStatus | null;
  createdBy?: string | null;
  createdByName?: string | null;
  createdAt: string;
}

/**
 * Body of the two recharge calls, per backend `RechargeRequest`.
 *
 * `POST /branch-wallet/recharge/order` uses `branchId` + `amount`: opens a gateway order
 * for exactly that amount. Nothing is credited.
 *
 * `POST /branch-wallet/recharge` uses the three gateway fields checkout hands back; the
 * server verifies the signature, asks the gateway what was actually paid, and credits
 * that — `amount` is ignored at this step.
 */
export interface RechargeRequest {
  branchId?: string | null;
  amount?: number | null;
  gatewayOrderId?: string | null;
  paymentReference?: string | null;
  signature?: string | null;
  remarks?: string | null;
}

/** Everything the browser checkout needs — mirrors backend `RechargeOrderResponse`.
 *  `amountMinor` is in paise, the gateway's contract. */
export interface RechargeOrderResponse {
  walletId: string;
  walletNumber: string;
  branchId: string;
  gateway: string;
  orderId: string;
  amountMinor: number;
  currency: string;
  publicKey: string;
  receipt: string;
}

/** Body of `POST /branch-wallet/credit` — `COMPANY_ADMIN` only. */
export interface CreditRequest {
  branchId: string;
  amount: number;
  subTransactionType?: SubTransactionType | null;
  referenceType?: ReferenceType | null;
  referenceId?: string | null;
  remarks?: string | null;
}

/** Body of `POST /branch-wallet/debit` — `COMPANY_ADMIN` only. */
export interface DebitRequest {
  branchId: string;
  amount: number;
  subTransactionType?: SubTransactionType | null;
  referenceType?: ReferenceType | null;
  referenceId?: string | null;
  remarks?: string | null;
}

export type TopupRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

/** Mirrors backend `TopupRequestResponse` — a branch's ask to fund its own wallet, and the
 *  company admin's decision on it. Distinct from a recharge: nothing moves until approved. */
export interface TopupRequest {
  id: string;
  companyId: string;
  walletId: string;
  branchId: string;
  requestedAmount: number;
  remarks?: string | null;
  status: TopupRequestStatus;
  requestedBy: string;
  createdAt: string;
  decidedBy?: string | null;
  decidedAt?: string | null;
  decisionRemarks?: string | null;
  transactionId?: string | null;
  version: number;
}

/** Body of `POST /branch-wallet/topup-requests`. */
export interface CreateTopupRequestRequest {
  branchId?: string | null;
  amount: number;
  remarks?: string | null;
}

/** Body of the approve/reject endpoints — both optional, a decision needs no reason. */
export interface DecideTopupRequestRequest {
  remarks?: string | null;
}

/** Query parameters of `GET /branch-wallet/topup-requests`. */
export interface TopupRequestSearchRequest {
  branchId?: string | null;
  status?: TopupRequestStatus | null;
}

/** Query parameters of `GET /branch-wallet/transactions`. `branchId` omitted uses the
 *  caller's own branch — there is deliberately no `walletId` filter. */
export interface WalletTransactionSearchRequest {
  branchId?: string | null;
  transactionType?: TransactionType[];
  subTransactionType?: SubTransactionType[];
  referenceType?: ReferenceType[];
  paymentStatus?: PaymentStatus[];
  referenceId?: string;
  transactionNo?: string;
  paymentReference?: string;
  fromDate?: string;   // yyyy-MM-dd
  toDate?: string;     // yyyy-MM-dd
  minAmount?: number;
  maxAmount?: number;
  search?: string;
}

/** Title-case an ENUM_TOKEN for display, e.g. BOOKING_CHARGE → "Booking Charge". */
export function prettyToken(v?: string | null): string {
  return (v || '').replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase());
}

/** Human label for a sub transaction type code, e.g. MCR → "Manual Credit". */
export function subTypeLabel(v?: SubTransactionType | null): string {
  return v ? (SUB_TRANSACTION_LABELS[v] ?? prettyToken(v)) : '—';
}

/** Format money in the wallet's currency. Defaults to INR; falls back gracefully if the
 *  Intl locale data lacks the currency. */
export function formatMoney(amount: number | null | undefined, currency = 'INR'): string {
  const n = amount ?? 0;
  try {
    return new Intl.NumberFormat('en-IN', { style: 'currency', currency, maximumFractionDigits: 2 }).format(n);
  } catch {
    return `${currency} ${n.toFixed(2)}`;
  }
}
