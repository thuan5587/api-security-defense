package com.crapi.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.crapi.model.DashboardResponse;
import com.crapi.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class UserControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private UserController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void dashboardShouldReturn401WhenTokenIsInvalid() {
        when(userService.getUserByRequestToken(request))
                .thenReturn(null);

        ResponseEntity<?> response = controller.dashboard(request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());

        verify(userService).getUserByRequestToken(request);
    }

    @Test
    void dashboardShouldReturn200WhenUserIsAuthenticated() {
        DashboardResponse dashboardResponse = mock(DashboardResponse.class);

        when(userService.getUserByRequestToken(request))
                .thenReturn(dashboardResponse);

        ResponseEntity<?> response = controller.dashboard(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(dashboardResponse, response.getBody());

        verify(userService).getUserByRequestToken(request);
    }
}
