package org.hvtra.identityservice.model.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class TokenExchangeResponse {
    String accessToken;

    String expiresIn;

    String refreshExpiresIn;

    String tokenType;

    String idToken;

    String scope;
}
