package com.harnessdg.audit.aspect;

import com.harnessdg.audit.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.lang.annotation.*;

@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditService auditService;

    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        Object result = joinPoint.proceed();

        try {
            String resourceId = resolveResourceId(joinPoint);
            auditService.log(
                    auditable.action(),
                    auditable.resourceType(),
                    resourceId,
                    auditable.detail()
            );
        } catch (Exception e) {
            log.warn("Failed to write audit log", e);
        }

        return result;
    }

    private String resolveResourceId(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args.length > 0 && args[0] != null) {
            return args[0].toString();
        }
        return "";
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    public @interface Auditable {
        String action();
        String resourceType();
        String detail() default "";
    }
}
