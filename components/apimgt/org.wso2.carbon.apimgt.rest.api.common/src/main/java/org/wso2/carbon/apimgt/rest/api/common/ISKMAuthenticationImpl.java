package org.wso2.carbon.apimgt.rest.api.common;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.APIMgtAuthorizationFailedException;
import org.wso2.carbon.apimgt.api.model.AccessTokenInfo;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.impl.RESTAPICacheConfiguration;
import org.wso2.carbon.apimgt.impl.internal.ServiceReferenceHolder;
import org.wso2.carbon.apimgt.impl.utils.APIUtil;
import org.wso2.carbon.apimgt.rest.api.common.RestAPIAuthenticator;
import org.wso2.carbon.apimgt.rest.api.common.RestApiConstants;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.identity.oauth2.OAuth2TokenValidationService;
import org.wso2.carbon.identity.oauth2.dto.OAuth2ClientApplicationDTO;
import org.wso2.carbon.identity.oauth2.dto.OAuth2TokenValidationRequestDTO;
import org.wso2.carbon.identity.oauth2.dto.OAuth2TokenValidationResponseDTO;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.user.core.service.RealmService;
import org.wso2.carbon.utils.CarbonUtils;
import org.wso2.carbon.utils.multitenancy.MultitenantConstants;
import org.wso2.carbon.utils.multitenancy.MultitenantUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ISKMAuthenticationImpl implements RestAPIAuthenticator {

    private static final Log log = LogFactory.getLog(ISKMAuthenticationImpl.class);
    private static final String SUPER_TENANT_SUFFIX =
            APIConstants.EMAIL_DOMAIN_SEPARATOR + MultitenantConstants.SUPER_TENANT_DOMAIN_NAME;

    @Override
    public boolean authenticate(HashMap<String, Object> message) throws APIMgtAuthorizationFailedException {
        AccessTokenInfo tokenInfo = null;

        String tenantDomain = MultitenantUtils.getTenantDomain(tokenInfo.getEndUserName());
        int tenantId;
        PrivilegedCarbonContext carbonContext = PrivilegedCarbonContext.getThreadLocalCarbonContext();
        RealmService realmService = (RealmService) carbonContext.getOSGiService(RealmService.class, null);
        try {
            String username = tokenInfo.getEndUserName();
            if (MultitenantConstants.SUPER_TENANT_DOMAIN_NAME.equals(tenantDomain)) {
                //when the username is an email in supertenant, it has at least 2 occurrences of '@'
                long count = username.chars().filter(ch -> ch == '@').count();
                //in the case of email, there will be more than one '@'
                boolean isEmailUsernameEnabled = Boolean.parseBoolean(CarbonUtils.getServerConfiguration().
                        getFirstProperty("EnableEmailUserName"));
                if (isEmailUsernameEnabled || (username.endsWith(SUPER_TENANT_SUFFIX) && count <= 1)) {
                    username = MultitenantUtils.getTenantAwareUsername(username);
                }
            }
            if (log.isDebugEnabled()) {
                log.debug("username = " + username);
            }
            tenantId = realmService.getTenantManager().getTenantId(tenantDomain);
            carbonContext.setTenantDomain(tenantDomain);
            carbonContext.setTenantId(tenantId);
            carbonContext.setUsername(username);
            if (!tenantDomain.equals(MultitenantConstants.SUPER_TENANT_DOMAIN_NAME)) {
                APIUtil.loadTenantConfigBlockingMode(tenantDomain);
            }
            return true;
        } catch (UserStoreException e) {
            log.error("Error while retrieving tenant id for tenant domain: " + tenantDomain, e);
        }
        return false;
    }

    @Override
    public boolean canHandle(HashMap<String, Object> message) {
        return true;
    }

    @Override
    public String getAuthenticationType() {
        return RestApiConstants.OAUTH2_AUTHENTICATION ;
    }

    @Override
    public int getPriority(HashMap<String, Object> message) {
        return 0;
    }
}
