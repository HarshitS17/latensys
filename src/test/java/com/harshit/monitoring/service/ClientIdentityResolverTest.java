package com.harshit.monitoring.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIdentityResolverTest {
    @Test
    void ignoresForwardingHeadersFromDirectClients() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.1.2.3");
        request.addHeader("X-Real-IP", "198.51.100.10");
        request.addHeader("X-Forwarded-For", "198.51.100.11");
        assertThat(new ClientIdentityResolver("172.30.40.10").resolve(request)).isEqualTo("10.1.2.3");
    }

    @Test
    void acceptsOnlyNumericAddressFromPinnedProxy() {
        ClientIdentityResolver resolver = new ClientIdentityResolver("172.30.40.10");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("172.30.40.10");
        request.addHeader("X-Real-IP", "198.51.100.10");
        request.addHeader("X-Forwarded-For", "198.51.100.11");
        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.10");
        request.removeHeader("X-Real-IP");
        request.addHeader("X-Real-IP", "attacker.example");
        assertThat(resolver.resolve(request)).isEqualTo("172.30.40.10");
    }
}
