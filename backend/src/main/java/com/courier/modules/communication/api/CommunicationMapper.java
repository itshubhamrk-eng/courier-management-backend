package com.courier.modules.communication.api;

import com.courier.modules.communication.api.dto.CommunicationDashboardResponse;
import com.courier.modules.communication.api.dto.CommunicationLogResponse;
import com.courier.modules.communication.api.dto.CommunicationLogSearchRequest;
import com.courier.modules.communication.api.dto.CommunicationSettingResponse;
import com.courier.modules.communication.api.dto.CommunicationTemplateResponse;
import com.courier.modules.communication.api.dto.CreateCommunicationTemplateRequest;
import com.courier.modules.communication.api.dto.UpdateCommunicationTemplateRequest;
import com.courier.modules.communication.api.dto.UpsertCommunicationSettingRequest;
import com.courier.modules.communication.application.CommunicationConfigJson;
import com.courier.modules.communication.application.CommunicationDashboardService.CommunicationDashboardSummary;
import com.courier.modules.communication.application.command.CreateCommunicationTemplateCommand;
import com.courier.modules.communication.application.command.UpdateCommunicationTemplateCommand;
import com.courier.modules.communication.application.command.UpsertCommunicationSettingCommand;
import com.courier.modules.communication.domain.CommunicationChannel;
import com.courier.modules.communication.domain.CommunicationLog;
import com.courier.modules.communication.domain.CommunicationLogCriteria;
import com.courier.modules.communication.domain.CommunicationSetting;
import com.courier.modules.communication.domain.CommunicationTemplate;
import com.courier.modules.communication.application.TemplateComponentsCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class CommunicationMapper {

    private final ObjectMapper objectMapper;

    public CreateCommunicationTemplateCommand toCommand(CreateCommunicationTemplateRequest r) {
        return new CreateCommunicationTemplateCommand(r.eventType(), r.channel(), r.templateName(),
                r.subject(), r.content());
    }

    public UpdateCommunicationTemplateCommand toCommand(UpdateCommunicationTemplateRequest r) {
        return new UpdateCommunicationTemplateCommand(r.templateName(), r.subject(), r.content(), r.status(),
                r.version(), r.category(), r.language(), r.headerText(), r.footerText(), r.buttons(),
                r.variableSamples());
    }

    public UpsertCommunicationSettingCommand toCommand(CommunicationChannel channel,
                                                        UpsertCommunicationSettingRequest r) {
        return new UpsertCommunicationSettingCommand(channel, r.enabled(), r.provider(), r.config(), r.secret());
    }

    public CommunicationLogCriteria toCriteria(CommunicationLogSearchRequest r) {
        CommunicationLogSearchRequest safe = r == null ? CommunicationLogSearchRequest.empty() : r;
        return new CommunicationLogCriteria(safe.shipmentId(), safe.customerId(), safe.eventType(),
                safe.channel(), safe.status());
    }

    public CommunicationTemplateResponse toResponse(CommunicationTemplate t) {
        return new CommunicationTemplateResponse(t.getId(), t.getEventType(), t.getChannel(), t.getTemplateName(),
                t.getSubject(), t.getContent(), t.getStatus(), t.getCreatedAt(), t.getUpdatedAt(), t.getVersion(),
                t.getMetaTemplateId(), t.getMetaStatus(), t.getMetaStatusDetail(), t.getMetaCategory(),
                t.getMetaLanguage(), t.getMetaSubmittedAt(), t.getCategory(), t.getLanguage(), t.getHeaderText(),
                t.getFooterText(), TemplateComponentsCodec.readButtons(objectMapper, t.getButtonsJson()),
                TemplateComponentsCodec.readSamples(objectMapper, t.getVariableSamplesJson()));
    }

    public CommunicationSettingResponse toResponse(CommunicationSetting s) {
        return new CommunicationSettingResponse(s.getId(), s.getChannel(), s.isEnabled(), s.getProvider(),
                CommunicationConfigJson.read(objectMapper, s.getConfigJson()), s.hasSecret(), s.getUpdatedAt());
    }

    public CommunicationLogResponse toResponse(CommunicationLog l) {
        return new CommunicationLogResponse(l.getId(), l.getShipmentId(), l.getCustomerId(), l.getEventType(),
                l.getChannel(), l.getRecipient(), l.getTemplateId(), l.getStatus(), l.getProviderMessageId(),
                l.getErrorMessage(), l.getAttemptCount(), l.getLastAttemptAt(), l.getNextRetryAt(), l.getSentAt(),
                l.getCreatedAt());
    }

    public CommunicationDashboardResponse toResponse(CommunicationDashboardSummary s) {
        Map<CommunicationChannel, CommunicationDashboardResponse.ChannelStats> channels =
                new EnumMap<>(CommunicationChannel.class);
        s.channels().forEach((channel, stats) -> channels.put(channel, new CommunicationDashboardResponse.ChannelStats(
                stats.sent(), stats.delivered(), stats.failed(), stats.pending(), stats.cancelled())));
        return new CommunicationDashboardResponse(s.totalSent(), s.totalDelivered(), s.totalFailed(),
                s.totalPending(), channels, s.period().name(), s.from(), s.to(), s.totalRead(),
                s.deliveryRatePercent(), s.readRatePercent(),
                s.daily().stream().map(d -> new CommunicationDashboardResponse.DayPoint(
                        d.date(), d.sent(), d.delivered(), d.failed())).toList(),
                s.recentFailures().stream().map(f -> new CommunicationDashboardResponse.FailureRow(
                        f.id(), f.shipmentId(), f.eventType(), f.channel(), f.recipient(), f.errorMessage(),
                        f.attemptCount(), f.at())).toList(),
                s.dailyReport() == null ? null : new CommunicationDashboardResponse.DailyReportStatus(
                        s.dailyReport().configured(), s.dailyReport().recipients(), s.dailyReport().lastDate(),
                        s.dailyReport().lastStatus(), s.dailyReport().lastSentAt(), s.dailyReport().lastDeliveredAt(),
                        s.dailyReport().lastReadAt(), s.dailyReport().lastError()),
                s.templates() == null ? null : new CommunicationDashboardResponse.TemplateHealth(
                        s.templates().approved(), s.templates().pending(), s.templates().rejected(),
                        s.templates().notSubmitted(), s.templates().switchedOff(), s.templates().total()),
                s.lastReceiptAt(),
                s.whatsAppCost() == null ? null : new CommunicationDashboardResponse.WhatsAppCost(
                        s.whatsAppCost().basis(), s.whatsAppCost().anyRateSet(), s.whatsAppCost().ratesComplete(),
                        s.whatsAppCost().lines().stream().map(l -> new CommunicationDashboardResponse.CostLine(
                                l.category(), l.messages(), l.rate(), l.amount())).toList(),
                        s.whatsAppCost().total(), s.whatsAppCost().currency()));
    }
}
