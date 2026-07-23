package org.hvtra.deliveryservice.service.ahmove;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.hvtra.deliveryservice.client.AhmoveClient;
import org.hvtra.deliveryservice.config.AhamoveProperties;
import org.hvtra.deliveryservice.exception.AhamoveIntegrationException;
import org.hvtra.deliveryservice.model.response.ahmove.AhamoveCityResponse;
import org.hvtra.deliveryservice.model.response.ahmove.AhamoveServiceResponse;
import org.hvtra.deliveryservice.model.response.ahmove.AhamoveSpecialRequestResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class AhamoveCatalogService {
    private final AhmoveClient client;
    private final AhamoveProperties properties;

    public List<AhamoveCityResponse> cities(String countryId) {
        properties.validate();
        String resolvedCountryId = countryId == null || countryId.isBlank() ? "VN" : countryId.trim().toUpperCase(Locale.ROOT);
        try {
            List<AhamoveCityResponse> result = client.getCities( resolvedCountryId);
            return result == null ? List.of() : result;
        } catch (FeignException exception) {
            throw integrationException("Get Ahamove cities failed", exception);
        }
    }

    public List<AhamoveServiceResponse> services(
            String cityId,
            BigDecimal latitude,
            BigDecimal longitude,
            String deliveryType
    ) {
        properties.validate();
        boolean hasCity = cityId != null && !cityId.isBlank();
        boolean hasCoordinates = latitude != null && longitude != null;
        if (!hasCity && !hasCoordinates) {
            throw new IllegalArgumentException("cityId or both latitude and longitude are required");
        }
        if ((latitude == null) != (longitude == null)) {
            throw new IllegalArgumentException("latitude and longitude must be provided together");
        }
        try {
            List<AhamoveServiceResponse> result = client.getServices(
                    hasCity ? cityId.trim().toUpperCase(Locale.ROOT) : null,
                    latitude == null ? null : latitude.stripTrailingZeros().toPlainString(),
                    longitude == null ? null : longitude.stripTrailingZeros().toPlainString(),
                    deliveryType == null || deliveryType.isBlank() ? null : deliveryType.trim().toUpperCase(Locale.ROOT)
            );
            return result == null ? List.of() : result;
        } catch (FeignException exception) {
            throw integrationException("Get Ahamove services failed", exception);
        }
    }

    public List<AhamoveServiceResponse> serviceDetail(String serviceId) {
        properties.validate();
        if (serviceId == null || serviceId.isBlank()) {
            throw new IllegalArgumentException("serviceId is required");
        }
        try {
            List<AhamoveServiceResponse> result = client.getServiceDetail(serviceId.trim());
            return result == null ? List.of() : result;
        } catch (FeignException exception) {
            throw integrationException("Get Ahamove service detail failed", exception);
        }
    }

    public List<AhamoveSpecialRequestResponse> specialRequests(String serviceId) {
        return serviceDetail(serviceId).stream()
                .flatMap(service -> service.requests() == null ? Stream.empty() : service.requests().stream())
                .filter(request -> !Boolean.FALSE.equals(request.enable()))
                .toList();
    }
    private AhamoveIntegrationException integrationException(String operation, FeignException exception) {
        String body = exception.contentUTF8();
        String detail = body == null || body.isBlank() ? exception.getMessage() : body;
        return new AhamoveIntegrationException(operation + " (HTTP " + exception.status() + "): " + detail, exception);
    }
}
