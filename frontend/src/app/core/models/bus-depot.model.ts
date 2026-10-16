/** MSRTC bus depot / station master — mirrors backend `/bus-depots` one-to-one. */
export type BusDepotType = 'BUS_DEPOT' | 'BUS_STATION';
export type BusDepotStatus = 'ACTIVE' | 'INACTIVE';

export const BUS_DEPOT_TYPE_LABEL: Record<BusDepotType, string> = {
  BUS_DEPOT: 'Bus Depot',
  BUS_STATION: 'Bus Station'
};

export interface BusDepot {
  id: string;
  depotCode: string;
  depotName: string;
  depotType: BusDepotType;
  status: BusDepotStatus;
  branchId?: string | null;
  branchCode?: string | null;
  branchName?: string | null;
  branchType?: string | null;
  divisionId?: string | null;
  divisionName?: string | null;
  parentDepotId?: string | null;
  parentDepotName?: string | null;
  address?: string | null;
  city?: string | null;
  district?: string | null;
  state?: string | null;
  pincode?: string | null;
  contactPerson?: string | null;
  contactMobile?: string | null;
  version: number;
}

export interface SaveBusDepotRequest {
  depotCode: string;
  depotName: string;
  depotType: BusDepotType;
  branchId: string | null;
  divisionId: string | null;
  parentDepotId: string | null;
  address: string | null;
  city: string | null;
  district: string | null;
  state: string | null;
  pincode: string | null;
  contactPerson: string | null;
  contactMobile: string | null;
}

export type UpdateBusDepotRequest = SaveBusDepotRequest & { version: number };

export interface BusDivision {
  id: string;
  divisionCode: string;
  divisionName: string;
  status: BusDepotStatus;
  depotCount: number;
  stationCount: number;
  version: number;
}

/** MSRTC crossing: a bus A -> B with no direct service goes via one or more depots / stations. */
export interface BusDepotRef {
  id: string;
  depotCode: string;
  depotName: string;
  depotType: BusDepotType;
  city?: string | null;
}

export interface BusCrossingRoute {
  id: string;
  status: BusDepotStatus;
  from: BusDepotRef;
  to: BusDepotRef;
  stops: BusDepotRef[];
  version: number;
}

export interface ShipmentBusCrossing {
  id: string;
  shipmentId: string;
  routeId?: string | null;
  from: BusDepotRef;
  to: BusDepotRef;
  stops: BusDepotRef[];
}

export interface BusCrossingPick {
  fromDepotId: string;
  toDepotId: string;
  stops: string[];
}

export function depotRefLabel(d: BusDepotRef): string {
  return `${d.depotName} (${d.depotCode}) · ${BUS_DEPOT_TYPE_LABEL[d.depotType]}`;
}
