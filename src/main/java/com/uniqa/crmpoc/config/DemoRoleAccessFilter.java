package com.uniqa.crmpoc.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * DEMO-ONLY access control: there is no real authentication in this POC.
 * The frontend's role switcher (see demoRole.ts) sends the picked role as the
 * X-Demo-Role header on every request; this filter rejects Rules API calls
 * that aren't ADMIN, so the demo shows real server-side enforcement rather
 * than just hiding the menu item client-side. A production build would
 * replace this with real authentication + Spring Security authorization.
 *
 * NLP has no equivalent guard: NlpView reads /api/emails, which Inbox also
 * uses, so there's no endpoint to lock without also blocking Inbox - NLP
 * access control is UI-only for now.
 */
@Component
public class DemoRoleAccessFilter extends OncePerRequestFilter {

    private static final String ROLE_HEADER = "X-Demo-Role";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/api/rules") && !"ADMIN".equals(request.getHeader(ROLE_HEADER))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Admin role required (demo access control)\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
