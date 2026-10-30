package com.crapi.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

public class SecurityRequestContextFilter extends OncePerRequestFilter {

  private static String sanitizeIp(String ip) {
    if (ip == null || ip.isBlank()) {
      return "N/A";
    }
    if (ip.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
      String[] parts = ip.split("\\.");
      for (String part : parts) {
        if (Integer.parseInt(part) > 255) {
          return "N/A";
        }
      }
      return parts[0] + "." + parts[1] + "." + parts[2] + ".0";
    }
    if (ip.contains(":") && ip.matches("[0-9a-fA-F:.%0-9A-Za-z_-]+")) {
      try {
        byte[] bytes = com.google.common.net.InetAddresses.forString(ip).getAddress();
        if (bytes.length == 16) {
          java.util.Arrays.fill(bytes, 6, 16, (byte) 0);
          return java.net.InetAddress.getByAddress(bytes).getHostAddress();
        }
      } catch (Exception ignored) {
        return "N/A";
      }
    }
    return "N/A";
  }
  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain)
      throws ServletException, IOException {

    try {
      MDC.put("requestId", UUID.randomUUID().toString());
      MDC.put("endpoint", request.getRequestURI());
      MDC.put("method", request.getMethod());
      MDC.put("clientIp", sanitizeIp(request.getRemoteAddr()));

      filterChain.doFilter(request, response);
    } finally {
      MDC.remove("requestId");
      MDC.remove("endpoint");
      MDC.remove("method");
      MDC.remove("principalId");
      MDC.remove("jwtSecurityEventLogged");
      MDC.remove("clientIp");
    }
  }
}