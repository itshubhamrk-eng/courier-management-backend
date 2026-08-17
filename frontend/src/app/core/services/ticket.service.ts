import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from '@core/services/api.service';
import { API } from '@core/config/api-endpoints';
import { PageQuery } from '@core/models/page.model';
import {
  AssignmentRequest, ChangeCategoryRequest, ChangePriorityRequest, ChangeStatusRequest,
  CreateTicketRequest, RemarksRequest, ReplyRequest, SlaRule, Ticket, TicketAttachment,
  TicketCategory, TicketDashboardStats, TicketDetail, TicketMessage, TicketSubCategory,
  UpsertSlaRuleRequest
} from '@core/models/ticket.model';

/** Ticket Support API — talks to `/api/v1/support/**` via ApiService, mirroring the
 *  backend one-to-one. See `MEMORY/AI_CONTEXT.md` for Phase 1 scope. */
@Injectable({ providedIn: 'root' })
export class TicketService {
  private readonly api = inject(ApiService);
  private readonly base = API.supportTickets;
  private readonly categoriesBase = API.supportCategories;
  private readonly slaRulesBase = API.supportSlaRules;

  // ---- tickets ----------------------------------------------------------------
  create(body: CreateTicketRequest): Observable<Ticket> {
    return this.api.post<Ticket>(this.base, body);
  }
  get(id: string): Observable<TicketDetail> {
    return this.api.get<TicketDetail>(`${this.base}/${id}`);
  }
  search(query: PageQuery) {
    return this.api.page<Ticket>(this.base, query);
  }
  dashboard(companyId?: string | null): Observable<TicketDashboardStats> {
    return this.api.get<TicketDashboardStats>(`${this.base}/dashboard`, companyId ? { companyId } : undefined);
  }

  // ---- conversation -------------------------------------------------------------
  reply(id: string, body: ReplyRequest): Observable<TicketMessage> {
    return this.api.post<TicketMessage>(`${this.base}/${id}/messages`, body);
  }
  uploadAttachment(id: string, file: File, messageId?: string | null): Observable<TicketAttachment> {
    const form = new FormData();
    form.append('file', file);
    if (messageId) form.append('messageId', messageId);
    return this.api.post<TicketAttachment>(`${this.base}/${id}/attachments`, form);
  }

  // ---- assignment -----------------------------------------------------------
  assign(id: string, body: AssignmentRequest): Observable<Ticket> {
    return this.api.patch<Ticket>(`${this.base}/${id}/assign`, body);
  }
  reassign(id: string, body: AssignmentRequest): Observable<Ticket> {
    return this.api.patch<Ticket>(`${this.base}/${id}/reassign`, body);
  }
  escalate(id: string, body: AssignmentRequest): Observable<Ticket> {
    return this.api.patch<Ticket>(`${this.base}/${id}/escalate`, body);
  }

  // ---- status / priority / category -----------------------------------------
  changeStatus(id: string, body: ChangeStatusRequest): Observable<Ticket> {
    return this.api.patch<Ticket>(`${this.base}/${id}/status`, body);
  }
  changePriority(id: string, body: ChangePriorityRequest): Observable<Ticket> {
    return this.api.patch<Ticket>(`${this.base}/${id}/priority`, body);
  }
  changeCategory(id: string, body: ChangeCategoryRequest): Observable<Ticket> {
    return this.api.patch<Ticket>(`${this.base}/${id}/category`, body);
  }
  reopen(id: string, body: RemarksRequest): Observable<Ticket> {
    return this.api.patch<Ticket>(`${this.base}/${id}/reopen`, body);
  }
  close(id: string, body: RemarksRequest): Observable<Ticket> {
    return this.api.patch<Ticket>(`${this.base}/${id}/close`, body);
  }

  // ---- categories (global catalogue, SUPER_ADMIN writes) ---------------------
  categories(): Observable<TicketCategory[]> {
    return this.api.get<TicketCategory[]>(this.categoriesBase);
  }
  subCategories(categoryId: string): Observable<TicketSubCategory[]> {
    return this.api.get<TicketSubCategory[]>(`${this.categoriesBase}/sub-categories`, { categoryId });
  }
  createCategory(name: string): Observable<TicketCategory> {
    return this.api.post<TicketCategory>(this.categoriesBase, { name });
  }
  renameCategory(id: string, name: string): Observable<TicketCategory> {
    return this.api.patch<TicketCategory>(`${this.categoriesBase}/${id}`, { name });
  }
  setCategoryActive(id: string, active: boolean): Observable<TicketCategory> {
    return this.api.patch<TicketCategory>(`${this.categoriesBase}/${id}/${active ? 'activate' : 'deactivate'}`, {});
  }
  createSubCategory(categoryId: string, name: string): Observable<TicketSubCategory> {
    return this.api.post<TicketSubCategory>(`${this.categoriesBase}/sub-categories`, { categoryId, name });
  }
  renameSubCategory(id: string, name: string): Observable<TicketSubCategory> {
    return this.api.patch<TicketSubCategory>(`${this.categoriesBase}/sub-categories/${id}`, { name });
  }
  setSubCategoryActive(id: string, active: boolean): Observable<TicketSubCategory> {
    return this.api.patch<TicketSubCategory>(
      `${this.categoriesBase}/sub-categories/${id}/${active ? 'activate' : 'deactivate'}`, {});
  }

  // ---- SLA rules (COMPANY_ADMIN writes, one row per priority) ----------------
  slaRules(): Observable<SlaRule[]> {
    return this.api.get<SlaRule[]>(this.slaRulesBase);
  }
  upsertSlaRule(body: UpsertSlaRuleRequest): Observable<SlaRule> {
    return this.api.post<SlaRule>(this.slaRulesBase, body);
  }
  setSlaRuleActive(id: string, active: boolean): Observable<SlaRule> {
    return this.api.patch<SlaRule>(`${this.slaRulesBase}/${id}/${active ? 'activate' : 'deactivate'}`, {});
  }
}
