package org.wso2.carbon.apimgt.keymgt.model.entity;

/**
 * Entity for keeping Group ID and Application ID details
 */
public class GroupId {
    private String groupId = null;
    private Integer applicationId = null;

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public Integer getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Integer applicationId) {
        this.applicationId = applicationId;
    }
}
