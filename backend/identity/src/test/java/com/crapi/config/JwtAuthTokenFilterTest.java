
package com.crapi.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.crapi.enums.EStatus;
import jakarta.servlet.http.HttpServletRequest;
import java.text.ParseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class JwtAuthTokenFilterTest {

    @Mock
    private JwtProvider tokenProvider;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private JwtAuthTokenFilter filter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void validApiKeyShouldBeAccepted() throws ParseException {
        String token = "valid-api-key";

        when(request.getHeader("Authorization"))
                .thenReturn("ApiKey " + token);

        when(tokenProvider.validateJwtToken(token))
                .thenReturn(true);

        when(tokenProvider.getUserNameFromJwtToken(token))
                .thenReturn("test@example.com");

        String result = filter.getUserFromToken(request);

        assertEquals("test@example.com", result);
        verify(tokenProvider).validateJwtToken(token);
    }

    @Test
    void forgedApiKeyShouldBeRejected() throws ParseException {
        String token = "forged-api-key";

        when(request.getHeader("Authorization"))
                .thenReturn("ApiKey " + token);

        when(tokenProvider.validateJwtToken(token))
                .thenReturn(false);

        String result = filter.getUserFromToken(request);

        assertEquals(EStatus.INVALID.toString(), result);

        verify(tokenProvider).validateJwtToken(token);
        verify(tokenProvider, never())
                .getUserNameFromJwtToken(anyString());
    }

    @Test
    void apiKeyWithoutExpirationShouldBeAccepted() throws ParseException {
        String token = "valid-api-key-without-exp";

        when(request.getHeader("Authorization"))
                .thenReturn("ApiKey " + token);

        when(tokenProvider.validateJwtToken(token))
                .thenReturn(true);

        when(tokenProvider.getUserNameFromJwtToken(token))
                .thenReturn("test@example.com");

        String result = filter.getUserFromToken(request);

        assertEquals("test@example.com", result);
        verify(tokenProvider).validateJwtToken(token);
    }

    @Test
    void missingApiKeyShouldBeRejected() throws ParseException {
        when(request.getHeader("Authorization"))
                .thenReturn(null);

        String result = filter.getUserFromToken(request);

        assertEquals(EStatus.INVALID.toString(), result);
        verify(tokenProvider, never())
                .validateJwtToken(anyString());
    }
}
