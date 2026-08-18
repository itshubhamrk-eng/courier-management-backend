package com.courier.modules.dashboard.api.dto;

import java.util.List;

/** One named line/bar on a dashboard trend chart, e.g. "Delivered" on Delivery Performance. */
public record ChartSeriesResponse(String name, List<ChartPointResponse> points) {
}
