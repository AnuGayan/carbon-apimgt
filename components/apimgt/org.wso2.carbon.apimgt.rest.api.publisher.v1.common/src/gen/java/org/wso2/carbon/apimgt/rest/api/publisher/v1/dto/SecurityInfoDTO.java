package org.wso2.carbon.apimgt.rest.api.publisher.v1.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.*;

/**
 * Optional security headers to use during URL validation.
 **/

import io.swagger.annotations.*;
import java.util.Objects;

import javax.xml.bind.annotation.*;
import org.wso2.carbon.apimgt.rest.api.common.annotations.Scope;
import com.fasterxml.jackson.annotation.JsonCreator;

import javax.validation.Valid;

@ApiModel(description = "Optional security headers to use during URL validation.")

public class SecurityInfoDTO   {
  
    private Boolean isSecure = false;
    private String header = null;
    private String value = null;
    private String dcrUrl = null;
    private String tokenUrl = null;
    private String username = null;
    private String password = null;
    private String grantType = null;
    private List<String> scopes = new ArrayList<String>();
    private String clientName = null;

  /**
   * Indicates whether the URL is secure (HTTPS) or not (HTTP).
   **/
  public SecurityInfoDTO isSecure(Boolean isSecure) {
    this.isSecure = isSecure;
    return this;
  }

  
  @ApiModelProperty(value = "Indicates whether the URL is secure (HTTPS) or not (HTTP).")
  @JsonProperty("isSecure")
  public Boolean isIsSecure() {
    return isSecure;
  }
  public void setIsSecure(Boolean isSecure) {
    this.isSecure = isSecure;
  }

  /**
   * Header name used for authentication, if required.
   **/
  public SecurityInfoDTO header(String header) {
    this.header = header;
    return this;
  }

  
  @ApiModelProperty(example = "Authorization", value = "Header name used for authentication, if required.")
  @JsonProperty("header")
  public String getHeader() {
    return header;
  }
  public void setHeader(String header) {
    this.header = header;
  }

  /**
   * Value used for the authentication header.
   **/
  public SecurityInfoDTO value(String value) {
    this.value = value;
    return this;
  }

  
  @ApiModelProperty(example = "Bearer <token>", value = "Value used for the authentication header.")
  @JsonProperty("value")
  public String getValue() {
    return value;
  }
  public void setValue(String value) {
    this.value = value;
  }

  /**
   * Endpoint URL for Dynamic Client Registration
   **/
  public SecurityInfoDTO dcrUrl(String dcrUrl) {
    this.dcrUrl = dcrUrl;
    return this;
  }


  @ApiModelProperty(example = "https://example.com/.well-known/oauth-authorization-server", value = "Endpoint URL for Dynamic Client Registration")
  @JsonProperty("dcrUrl")
  public String getDcrUrl() {
    return dcrUrl;
  }
  public void setDcrUrl(String dcrUrl) {
    this.dcrUrl = dcrUrl;
  }

  /**
   * Token Endpoint URL
   **/
  public SecurityInfoDTO tokenUrl(String tokenUrl) {
    this.tokenUrl = tokenUrl;
    return this;
  }


  @ApiModelProperty(example = "https://example.com/token", value = "Token Endpoint URL")
  @JsonProperty("tokenUrl")
  public String getTokenUrl() {
    return tokenUrl;
  }
  public void setTokenUrl(String tokenUrl) {
    this.tokenUrl = tokenUrl;
  }

  /**
   * Username for authentication
   **/
  public SecurityInfoDTO username(String username) {
    this.username = username;
    return this;
  }


  @ApiModelProperty(example = "admin", value = "Username for authentication")
  @JsonProperty("username")
  public String getUsername() {
    return username;
  }
  public void setUsername(String username) {
    this.username = username;
  }

  /**
   * Password for authentication
   **/
  public SecurityInfoDTO password(String password) {
    this.password = password;
    return this;
  }


  @ApiModelProperty(example = "password", value = "Password for authentication")
  @JsonProperty("password")
  public String getPassword() {
    return password;
  }
  public void setPassword(String password) {
    this.password = password;
  }

  /**
   * Grant type for authentication
   **/
  public SecurityInfoDTO grantType(String grantType) {
    this.grantType = grantType;
    return this;
  }


  @ApiModelProperty(example = "password", value = "Grant type for authentication")
  @JsonProperty("grantType")
  public String getGrantType() {
    return grantType;
  }
  public void setGrantType(String grantType) {
    this.grantType = grantType;
  }

  /**
   * Scopes for authentication
   **/
  public SecurityInfoDTO scopes(List<String> scopes) {
    this.scopes = scopes;
    return this;
  }


  @ApiModelProperty(example = "[\"scope1\",\"scope2\"]", value = "Scopes for authentication")
  @JsonProperty("scopes")
  public List<String> getScopes() {
    return scopes;
  }
  public void setScopes(List<String> scopes) {
    this.scopes = scopes;
  }

  /**
   * Client Name for DCR
   **/
  public SecurityInfoDTO clientName(String clientName) {
    this.clientName = clientName;
    return this;
  }


  @ApiModelProperty(example = "WSO2_APIM_Client", value = "Client Name for DCR")
  @JsonProperty("clientName")
  public String getClientName() {
    return clientName;
  }
  public void setClientName(String clientName) {
    this.clientName = clientName;
  }


  @Override
  public boolean equals(java.lang.Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SecurityInfoDTO securityInfo = (SecurityInfoDTO) o;
    return Objects.equals(isSecure, securityInfo.isSecure) &&
        Objects.equals(header, securityInfo.header) &&
        Objects.equals(value, securityInfo.value) &&
        Objects.equals(dcrUrl, securityInfo.dcrUrl) &&
        Objects.equals(tokenUrl, securityInfo.tokenUrl) &&
        Objects.equals(username, securityInfo.username) &&
        Objects.equals(password, securityInfo.password) &&
        Objects.equals(grantType, securityInfo.grantType) &&
        Objects.equals(scopes, securityInfo.scopes) &&
        Objects.equals(clientName, securityInfo.clientName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(isSecure, header, value, dcrUrl, tokenUrl, username, password, grantType, scopes, clientName);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class SecurityInfoDTO {\n");
    
    sb.append("    isSecure: ").append(toIndentedString(isSecure)).append("\n");
    sb.append("    header: ").append(toIndentedString(header)).append("\n");
    sb.append("    value: ").append(toIndentedString(value)).append("\n");
    sb.append("    dcrUrl: ").append(toIndentedString(dcrUrl)).append("\n");
    sb.append("    tokenUrl: ").append(toIndentedString(tokenUrl)).append("\n");
    sb.append("    username: ").append(toIndentedString(username)).append("\n");
    sb.append("    password: ").append(toIndentedString(password)).append("\n");
    sb.append("    grantType: ").append(toIndentedString(grantType)).append("\n");
    sb.append("    scopes: ").append(toIndentedString(scopes)).append("\n");
    sb.append("    clientName: ").append(toIndentedString(clientName)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(java.lang.Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }
}

