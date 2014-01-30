/*******************************************************************************
 * Copyright 2008(c) The OBiBa Consortium. All rights reserved.
 *
 * This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package org.obiba.opal.core.runtime.security;

import java.util.Collection;
import java.util.Set;

import javax.annotation.PreDestroy;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.ModularRealmAuthorizer;
import org.apache.shiro.authz.permission.PermissionResolverAware;
import org.apache.shiro.authz.permission.RolePermissionResolver;
import org.apache.shiro.authz.permission.RolePermissionResolverAware;
import org.apache.shiro.cache.MemoryConstrainedCacheManager;
import org.apache.shiro.config.Ini;
import org.apache.shiro.config.IniSecurityManagerFactory;
import org.apache.shiro.mgt.DefaultSecurityManager;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.realm.text.IniRealm;
import org.apache.shiro.session.SessionListener;
import org.apache.shiro.session.mgt.DefaultSessionManager;
import org.apache.shiro.session.mgt.ExecutorServiceSessionValidationScheduler;
import org.apache.shiro.session.mgt.SessionValidationScheduler;
import org.apache.shiro.util.LifecycleUtils;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.collect.ImmutableList;

@Component
public class OpalSecurityManagerFactory implements FactoryBean<SecurityManager> {

  private static final long SESSION_VALIDATION_INTERVAL = 3600000l; // 1 hour

  private final Set<Realm> realms;

  private final Set<SessionListener> sessionListeners;

  private final RolePermissionResolver rolePermissionResolver;

  private SecurityManager securityManager;

  @Autowired
  public OpalSecurityManagerFactory(Set<Realm> securityRealms, Set<SessionListener> sessionListeners,
      RolePermissionResolver rolePermissionResolver) {
    realms = securityRealms;
    this.sessionListeners = sessionListeners;
    this.rolePermissionResolver = rolePermissionResolver;
  }

  @Override
  public SecurityManager getObject() throws Exception {
    if(securityManager == null) {
      securityManager = doCreateSecurityManager();
      SecurityUtils.setSecurityManager(securityManager);
    }
    return securityManager;
  }

  @Override
  public Class<?> getObjectType() {
    return DefaultSecurityManager.class;
  }

  @Override
  public boolean isSingleton() {
    return true;
  }

  @PreDestroy
  public void destroySecurityManager() {
    // Destroy the security manager.
    SecurityUtils.setSecurityManager(null);
    LifecycleUtils.destroy(securityManager);
    securityManager = null;
  }

  private SecurityManager doCreateSecurityManager() {
    return new CustomIniSecurityManagerFactory(System.getProperty("OPAL_HOME") + "/conf/shiro.ini").createInstance();
  }

  private class CustomIniSecurityManagerFactory extends IniSecurityManagerFactory {

    private CustomIniSecurityManagerFactory(String resourcePath) {
      super(resourcePath);
    }

    @Override
    protected SecurityManager createDefaultInstance() {
      DefaultSecurityManager dsm = new DefaultSecurityManager();

      if(dsm.getCacheManager() == null) {
        dsm.setCacheManager(new MemoryConstrainedCacheManager());
      }

      if(dsm.getSessionManager() instanceof DefaultSessionManager) {
        DefaultSessionManager sessionManager = (DefaultSessionManager) dsm.getSessionManager();
        sessionManager.setSessionListeners(sessionListeners);
        SessionValidationScheduler sessionValidationScheduler = new ExecutorServiceSessionValidationScheduler();
        sessionValidationScheduler.enableSessionValidation();
        sessionManager.setSessionValidationScheduler(sessionValidationScheduler);
        sessionManager.setSessionValidationInterval(SESSION_VALIDATION_INTERVAL);
      }

      if(dsm.getAuthorizer() instanceof ModularRealmAuthorizer) {
        ((RolePermissionResolverAware) dsm.getAuthorizer()).setRolePermissionResolver(rolePermissionResolver);
        ((PermissionResolverAware) dsm.getAuthorizer()).setPermissionResolver(new OpalPermissionResolver());
      }
      return dsm;
    }

    @Override
    protected void applyRealmsToSecurityManager(Collection<Realm> shiroRealms, @SuppressWarnings(
        "ParameterHidesMemberVariable") SecurityManager securityManager) {
      super.applyRealmsToSecurityManager(ImmutableList.<Realm>builder().addAll(realms).addAll(shiroRealms).build(),
          securityManager);
    }

    @Override
    protected Realm createRealm(Ini ini) {
      // Overridden to workaround issue https://issues.apache.org/jira/browse/SHIRO-322
      IniRealm realm = new IniRealm(System.getProperty("OPAL_HOME") + "/conf/shiro.ini") {
        @Override
        protected void onInit() {
          roles.clear();
          users.clear();
          super.onInit();
        }
      };
      realm.setName(INI_REALM_NAME);
      return realm;
    }
  }
}
