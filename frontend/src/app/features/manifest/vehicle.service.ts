import { Injectable, inject } from '@angular/core';
import { ApiService } from '@core/services/api.service';
import { API } from '@core/config/api-endpoints';
import { Vehicle, CreateVehicleRequest } from '@core/models/shipment.model';

/** The fleet Dispatch's "Assign Vehicle" picker reads from — /api/v1/vehicles. */
@Injectable({ providedIn: 'root' })
export class VehicleService {
  private readonly api = inject(ApiService);

  list(activeOnly = true) {
    return this.api.get<Vehicle[]>(API.vehicles, { activeOnly });
  }
  get(id: string) { return this.api.get<Vehicle>(`${API.vehicles}/${id}`); }
  create(body: CreateVehicleRequest) { return this.api.post<Vehicle>(API.vehicles, body); }
  activate(id: string) { return this.api.patch<Vehicle>(`${API.vehicles}/${id}/activate`, {}); }
  deactivate(id: string) { return this.api.patch<Vehicle>(`${API.vehicles}/${id}/deactivate`, {}); }
}
