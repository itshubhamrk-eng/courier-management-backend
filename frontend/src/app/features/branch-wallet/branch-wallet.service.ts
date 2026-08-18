import { Injectable, inject } from '@angular/core';
import { ApiService } from '@core/services/api.service';
import { API } from '@core/config/api-endpoints';
import { PageQuery } from '@core/models/page.model';
import {
  WalletResponse, WalletSummary, WalletTransaction,
  RechargeRequest, RechargeOrderResponse, CreditRequest, DebitRequest,
  TopupRequest, CreateTopupRequestRequest, DecideTopupRequestRequest
} from '@core/models/wallet.model';

/**
 * Branch Wallet API — talks to `/api/v1/branch-wallet` via ApiService, mirroring the
 * backend one-to-one; no mock data. There is deliberately no wallet id anywhere: a caller
 * gets their own branch's wallet, and a `COMPANY_ADMIN` selects another branch's with
 * `branchId`. A wallet is created with its branch, so there is no create/delete here —
 * only reads, the money operations (recharge / credit / debit) and the ledger.
 */
@Injectable({ providedIn: 'root' })
export class BranchWalletService {
  private readonly api = inject(ApiService);
  private readonly base = API.branchWallet;

  // ---- reads ----------------------------------------------------------------
  /** The wallet in full. Omit `branchId` for your own branch; a company admin may pass one. */
  get(branchId?: string | null) {
    return this.api.get<WalletResponse>(this.base, branchId ? { branchId } : undefined);
  }
  /** Balances plus today's / month's / lifetime credit and debit, and the last recharge. */
  summary(branchId?: string | null) {
    return this.api.get<WalletSummary>(`${this.base}/summary`, branchId ? { branchId } : undefined);
  }
  /** One `summary` row per active branch of the company — `COMPANY_ADMIN`/`FINANCE_USER`
   *  only. Powers the Finance Report's company-wide table. */
  companySummary() {
    return this.api.get<WalletSummary[]>(`${this.base}/company-summary`);
  }
  /** The ledger: paged, sorted, filtered, searchable. Array/enum filters (transactionType,
   *  subTransactionType, paymentStatus) travel through PageQuery's index signature the same
   *  way every other filtered list in this app does — cast to `string` by the caller,
   *  serialised as repeated params by ApiService at runtime. */
  transactions(query: PageQuery) {
    return this.api.page<WalletTransaction>(`${this.base}/transactions`, query);
  }

  // ---- recharge ---------------------------------------------------------------
  /** Step one: opens a gateway order for the amount. Nothing is credited yet. */
  openRechargeOrder(body: RechargeRequest) {
    return this.api.post<RechargeOrderResponse>(`${this.base}/recharge/order`, body);
  }
  /** Step two: settles the payment checkout produced. Idempotent on `paymentReference`. */
  recharge(body: RechargeRequest) {
    return this.api.post<WalletTransaction>(`${this.base}/recharge`, body);
  }

  // ---- manual money ops (company admin) ------------------------------------
  credit(body: CreditRequest) { return this.api.post<WalletTransaction>(`${this.base}/credit`, body); }
  debit(body: DebitRequest) { return this.api.post<WalletTransaction>(`${this.base}/debit`, body); }

  // ---- top-up requests (branch asks, company admin decides) -----------------
  /** Raise a request. A branch caller's own branch is used regardless of `branchId`. */
  createTopupRequest(body: CreateTopupRequestRequest) {
    return this.api.post<TopupRequest>(`${this.base}/topup-requests`, body);
  }
  /** A branch caller sees only their own branch's requests; a company admin sees all. */
  topupRequests(query: PageQuery) {
    return this.api.page<TopupRequest>(`${this.base}/topup-requests`, query);
  }
  approveTopupRequest(id: string, body: DecideTopupRequestRequest) {
    return this.api.patch<TopupRequest>(`${this.base}/topup-requests/${id}/approve`, body);
  }
  rejectTopupRequest(id: string, body: DecideTopupRequestRequest) {
    return this.api.patch<TopupRequest>(`${this.base}/topup-requests/${id}/reject`, body);
  }
}
