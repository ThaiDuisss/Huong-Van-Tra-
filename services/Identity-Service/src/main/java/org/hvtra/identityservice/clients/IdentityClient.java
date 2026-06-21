//package org.hvtra.identityservice.clients;
//
//import feign.QueryMap;
//import org.hvtra.identityservice.model.request.TokenParamExchange;
//import org.hvtra.identityservice.model.response.TokenExchangeResponse;
//import org.springframework.cloud.openfeign.FeignClient;
//import org.springframework.http.MediaType;
//import org.springframework.web.bind.annotation.PostMapping;
//
//@FeignClient(name = "identity-client", url = "${idp.url}")
//public interface IdentityClient {
//    @PostMapping(
//            value = "/realms/${idp.realm}/protocol/openid-connect/token",
//            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE
//    )
//    TokenExchangeResponse exchangeClientToken(@QueryMap()TokenParamExchange tokenParamExchange);
//
//    @PostMapping(
//            value = "/realms/${idp.realm}/protocol/openid-connect/token",
//            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE
//    )
//    TokenExchangeResponse exchangeClientToken(@QueryMap()TokenParamExchange tokenParamExchange);
//}
