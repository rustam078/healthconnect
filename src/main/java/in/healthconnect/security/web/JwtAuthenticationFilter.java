package in.healthconnect.security.web;

import in.healthconnect.security.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

// Reads the Bearer token on each request and, if valid, puts the user + authorities in the
// security context. It only IDENTIFIES the user - the authorization decision is made centrally.
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String token = bearerToken(request);
        if (token != null && jwtService.isValid(token)) {
            setAuthentication(token, request);
        }
        chain.doFilter(request, response);
    }

    private void setAuthentication(String token, HttpServletRequest request) {
        List<String> authorities = jwtService.getAuthorities(token);
        var granted = (authorities == null ? List.<String>of() : authorities).stream()
                .map(SimpleGrantedAuthority::new).toList();
        var auth = new UsernamePasswordAuthenticationToken(jwtService.getUsername(token), null, granted);
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private String bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return (header != null && header.startsWith("Bearer ")) ? header.substring(7) : null;
    }
}
