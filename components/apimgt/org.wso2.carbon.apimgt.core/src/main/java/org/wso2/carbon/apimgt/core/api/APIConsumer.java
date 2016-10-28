/*
 *
 *   Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *   WSO2 Inc. licenses this file to you under the Apache License,
 *   Version 2.0 (the "License"); you may not use this file except
 *   in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */

package org.wso2.carbon.apimgt.core.api;

import org.wso2.carbon.apimgt.core.exception.APIManagementException;
import org.wso2.carbon.apimgt.core.models.APISummaryResults;
import org.wso2.carbon.apimgt.core.models.Application;

import java.util.Map;

/**
 * This interface used to write Store specific methods.
 *
 */
public interface APIConsumer extends APIManager {


    /**
     * Returns a paginated list of all APIs in given Status list. If a given API has multiple APIs,
     * only the latest version will be included in this list.
     * 
     * @param offset offset
     * @param limit limit
     * @param status One or more Statuses
     * @param returnAPITags If true, tags of each API is returned
     * @return set of API
     * @throws APIManagementException if failed to API set
     */
    Map<String, Object> getAllAPIsByStatus(int offset, int limit, String[] status, boolean returnAPITags)
            throws APIManagementException;
    
   
    /**
     * Returns a paginated list of all APIs which match the given search criteria.
     *   
     * @param searchContent searchContent
     * @param searchType searchType
     * @param offset offset
     * @param limit limit
     * @return APISummaryResults
     * @throws APIManagementException
     */
    APISummaryResults searchAPIs(String searchContent, String searchType, int offset, int limit)
            throws APIManagementException;

    /**
     * Returns the corresponding application given the uuid
     * @param uuid uuid of the Application
     * @return it will return Application corresponds to the uuid provided.
     * @throws APIManagementException
     */
    Application getApplicationByUUID(String uuid) throws APIManagementException;

    /**
     * Function to remove an Application from the API Store
     * @param application - The Application Object that represents the Application
     * @throws APIManagementException
     */
    void removeApplication(Application application) throws APIManagementException;

    /**
     * Adds an application
     *
     * @param application Application
     * @param userId      User Id
     * @return Id of the newly created application
     * @throws APIManagementException if failed to add Application
     */
    int addApplication(Application application, String userId) throws APIManagementException;

    /**
     * Returns the corresponding application given the Id
     * @param id Id of the Application
     * @return it will return Application corresponds to the id.
     * @throws APIManagementException
     */
    Application getApplicationById(int id) throws APIManagementException;

}
