package org.hvtra.identityservice.model.request;

import lombok.*;

@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class TokenParamExchange {
    String grant_type;

    String client_id;

    String client_secret;

    String scope;
}
