package io.github.slimanitz.medirdv.platform.security;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Ownership checks in controllers answer a 403 problem detail with a French message. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AccessDeniedProblemAdvice {

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail accessDenied(AccessDeniedException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                e.getMessage() == null || e.getMessage().startsWith("Access Denied")
                        ? "Vous n'avez pas accès à cette ressource." : e.getMessage());
        problem.setTitle("Forbidden");
        return problem;
    }
}
