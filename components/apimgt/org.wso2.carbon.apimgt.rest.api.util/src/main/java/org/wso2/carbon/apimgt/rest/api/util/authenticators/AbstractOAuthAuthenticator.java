/*
 *
 *  Copyright (c) 2022, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.wso2.carbon.apimgt.rest.api.util.authenticators;

import org.apache.cxf.message.Message;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.api.model.AccessTokenInfo;
import org.wso2.carbon.apimgt.impl.caching.CacheProvider;
import org.wso2.carbon.apimgt.impl.dto.APIKeyValidationInfoDTO;
import org.wso2.carbon.apimgt.impl.utils.APIUtil;

import javax.cache.Cache;

/**
 * This class implemented for common methods of JWT and Opaque Authentications
 */
public abstract class AbstractOAuthAuthenticator {

    /**
     * @param message cxf message to be authenticated
     * @return true if authentication was successful else false
     * @throws APIManagementException when error in authentication process
     */
    public abstract boolean authenticate(Message message) throws APIManagementException;

    /**
     * @return rest API token cache
     */
    public Cache getRESTAPITokenCache() {
        return CacheProvider.getRESTAPITokenCache();
    }

    /**
     * @return rest API invalid token cache
     */
    public Cache getRESTAPIInvalidTokenCache() {
        return CacheProvider.getRESTAPIInvalidTokenCache();
    }

    /**
     * @param accessTokenInfo Token information associated with incoming request
     * @return return true if the token is expired
     */
    public boolean isAccessTokenExpired(AccessTokenInfo accessTokenInfo) {
        APIKeyValidationInfoDTO infoDTO = new APIKeyValidationInfoDTO();
        infoDTO.setValidityPeriod(accessTokenInfo.getValidityPeriod());
        infoDTO.setIssuedTime(accessTokenInfo.getIssuedTime());
        return APIUtil.isAccessTokenExpired(infoDTO);
    }
}
