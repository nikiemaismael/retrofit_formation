package com.bank.core.infrastructure.adapter.in.web.jsf;

import com.bank.core.infrastructure.adapter.in.web.error.ErrorCode;
import com.bank.core.infrastructure.adapter.in.web.error.ErrorIdGenerator;
import com.bank.core.infrastructure.adapter.in.web.error.ErrorLogger;
import jakarta.faces.FacesException;
import jakarta.faces.application.ViewExpiredException;
import jakarta.faces.context.ExceptionHandler;
import jakarta.faces.context.ExceptionHandlerWrapper;
import jakarta.faces.context.FacesContext;
import jakarta.faces.event.ExceptionQueuedEvent;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import java.util.Iterator;

/**
 * Intercepte les exceptions non gérées du cycle de vie JSF, les journalise
 * avec un {@code errorId}, puis redirige vers une page d'erreur générique
 * qui n'affiche QUE l'{@code errorId} — jamais la trace technique.
 */
public class JsfExceptionHandler extends ExceptionHandlerWrapper {

    private static final Logger log = LoggerFactory.getLogger(JsfExceptionHandler.class);

    public JsfExceptionHandler(ExceptionHandler wrapped) {
        super(wrapped);
    }

    @Override
    public void handle() throws FacesException {
        FacesContext ctx = FacesContext.getCurrentInstance();
        if (ctx == null) {
            super.handle();
            return;
        }

        for (Iterator<ExceptionQueuedEvent> it = getUnhandledExceptionQueuedEvents().iterator(); it.hasNext(); ) {
            Throwable t = unwrap(it.next().getContext().getException());
            try {
                String contextPath = ctx.getExternalContext().getRequestContextPath();

                if (t instanceof ViewExpiredException) {
                    ctx.getExternalContext().redirect(contextPath + "/login.xhtml?expired=1");
                    return;
                }

                WebApplicationContext appCtx = springContext(ctx);
                ErrorIdGenerator idGen = appCtx.getBean(ErrorIdGenerator.class);
                ErrorLogger errorLogger = appCtx.getBean(ErrorLogger.class);

                String errorId = idGen.newErrorId();
                errorLogger.log(errorId, ErrorCode.INTERNAL_ERROR, requestUri(ctx), t);

                ctx.getExternalContext().getSessionMap().put("lastErrorId", errorId);
                ctx.getExternalContext().redirect(contextPath + "/error.xhtml");
            } catch (Exception redirectFailure) {
                log.error("Echec de redirection vers la page d'erreur JSF", redirectFailure);
            } finally {
                it.remove();
            }
        }
        super.handle();
    }

    private WebApplicationContext springContext(FacesContext ctx) {
        ServletContext sc = (ServletContext) ctx.getExternalContext().getContext();
        return WebApplicationContextUtils.getRequiredWebApplicationContext(sc);
    }

    private Throwable unwrap(Throwable t) {
        Throwable root = t;
        while (root instanceof FacesException && root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root;
    }

    private String requestUri(FacesContext ctx) {
        Object req = ctx.getExternalContext().getRequest();
        return (req instanceof HttpServletRequest hsr) ? hsr.getRequestURI() : "jsf-view";
    }
}
