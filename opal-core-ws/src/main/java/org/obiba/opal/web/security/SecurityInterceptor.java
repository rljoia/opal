/*******************************************************************************
 * Copyright 2008(c) The OBiBa Consortium. All rights reserved.
 * 
 * This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package org.obiba.opal.web.security;

import java.lang.annotation.Annotation;
import java.util.List;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Cookie;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response.Status;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.mgt.SessionsSecurityManager;
import org.apache.shiro.session.Session;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.jboss.resteasy.annotations.interception.ServerInterceptor;
import org.jboss.resteasy.core.ResourceMethod;
import org.jboss.resteasy.core.ServerResponse;
import org.jboss.resteasy.spi.Failure;
import org.jboss.resteasy.spi.HttpRequest;
import org.jboss.resteasy.spi.interception.PostProcessInterceptor;
import org.jboss.resteasy.spi.interception.PreProcessInterceptor;
import org.jboss.resteasy.util.HttpHeaderNames;
import org.obiba.opal.web.ws.security.AuthenticatedByCookie;
import org.obiba.opal.web.ws.security.NotAuthenticated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 *
 */
// @Component
@ServerInterceptor
public class SecurityInterceptor extends AbstractSecurityComponent implements PreProcessInterceptor, PostProcessInterceptor {

  private static final Logger log = LoggerFactory.getLogger(SecurityInterceptor.class);

  private static final String X_OPAL_AUTH = "X-Opal-Auth";

  private static final String OPAL_SESSION_ID_COOKIE_NAME = "opalsid";

  @Autowired
  public SecurityInterceptor(SessionsSecurityManager securityManager) {
    super(securityManager);
  }

  @Override
  public ServerResponse preProcess(HttpRequest request, ResourceMethod method) throws Failure, WebApplicationException {
    if(ThreadContext.getSubject() != null) {
      log.warn("Previous executing subject was not properly unbound from executing thread. Unbinding now.");
      ThreadContext.unbindSubject();
    }

    // Pass-through for methods (or resources) that do not require authentication
    if(method.getMethod().isAnnotationPresent(NotAuthenticated.class) || method.getResourceClass().isAnnotationPresent(NotAuthenticated.class)) {
      return null;
    }
    return authenticate(request, method);
  }

  @Override
  public void postProcess(ServerResponse response) {
    try {
      if(SecurityUtils.getSubject().isAuthenticated()) {
        Session session = SecurityUtils.getSubject().getSession();
        session.touch();
        int timeout = (int) (session.getTimeout() / 1000);
        response.getMetadata().add(HttpHeaderNames.SET_COOKIE, new NewCookie(OPAL_SESSION_ID_COOKIE_NAME, session.getId().toString(), "/", null, null, timeout, false));
      } else {
        if(isWebServiceAuthenticated(response.getAnnotations())) {
          // Only web service calls that require authentication will lose their opalsid cookie
          response.getMetadata().add(HttpHeaderNames.SET_COOKIE, new NewCookie(OPAL_SESSION_ID_COOKIE_NAME, null, "/", null, "Opal session deleted", 0, false));
        }
      }

      if(log.isDebugEnabled()) {
        Subject s = ThreadContext.getSubject();
        if(s != null) {
          Session session = s.getSession(false);
          log.debug("Unbinding subject {} session {} from executing thread {}", new Object[] { s.getPrincipal(), (session != null ? session.getId() : "null"), Thread.currentThread().getId() });
        }
      }

    } finally {
      ThreadContext.unbindSubject();
    }
  }

  private ServerResponse authenticate(HttpRequest request, ResourceMethod method) {
    String sessionId = extractCookieValue(request, method);
    String authorization = getFirstHeaderValue(HttpHeaders.AUTHORIZATION, request);
    if(isValidSessionId(sessionId)) {
      Subject s = new Subject.Builder(getSecurityManager()).sessionId(sessionId).buildSubject();
      s.getSession().touch();
      log.debug("Binding subject {} session {} to executing thread {}", new Object[] { s.getPrincipal(), sessionId, Thread.currentThread().getId() });
      ThreadContext.bind(s);
    } else if(authorization != null) {
      HttpAuthorizationToken token = new HttpAuthorizationToken(X_OPAL_AUTH, authorization);
      SecurityUtils.getSubject().login(token);
      sessionId = SecurityUtils.getSubject().getSession().getId().toString();
      log.info("Successfull session creation for user '{}' session ID is '{}'.", token.getUsername(), sessionId);
    } else {
      return (ServerResponse) ServerResponse.status(Status.UNAUTHORIZED).header(HttpHeaders.WWW_AUTHENTICATE, X_OPAL_AUTH + " realm=\"Opal\"").build();
    }
    return null;
  }

  private boolean isWebServiceAuthenticated(Annotation[] annotations) {
    for(Annotation annotation : annotations) {
      if(annotation instanceof NotAuthenticated) return false;
    }
    return true;
  }

  private String extractCookieValue(HttpRequest request, ResourceMethod method) {
    if(method.getMethod().isAnnotationPresent(AuthenticatedByCookie.class) || method.getResourceClass().isAnnotationPresent(AuthenticatedByCookie.class)) {
      Cookie cookie = request.getHttpHeaders().getCookies().get(OPAL_SESSION_ID_COOKIE_NAME);
      if(cookie != null) {
        return cookie.getValue();
      }
    } else {
      return getFirstHeaderValue(X_OPAL_AUTH, request);
    }
    return null;
  }

  private String getFirstHeaderValue(String header, HttpRequest request) {
    List<String> values = request.getHttpHeaders().getRequestHeader(header);
    if(values != null && values.size() == 1) {
      return values.get(0);
    }
    return null;
  }
}
