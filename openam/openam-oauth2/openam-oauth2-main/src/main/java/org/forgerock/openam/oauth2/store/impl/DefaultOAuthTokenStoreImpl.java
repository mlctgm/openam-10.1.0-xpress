/*
 * DO NOT REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 ForgeRock Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://forgerock.org/license/CDDLv1.0.html
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at http://forgerock.org/license/CDDLv1.0.html
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [2012] [Forgerock Inc]"
 */

package org.forgerock.openam.oauth2.store.impl;

import java.security.AccessController;
import java.util.HashMap;
import java.util.Set;
import java.util.Map;
import java.util.UUID;

import com.iplanet.sso.SSOToken;
import com.sun.identity.security.AdminTokenAction;
import com.sun.identity.sm.ServiceConfig;
import com.sun.identity.sm.ServiceConfigManager;
import org.forgerock.json.fluent.JsonValue;
import org.forgerock.json.resource.JsonResource;
import org.forgerock.json.resource.JsonResourceAccessor;
import org.forgerock.json.resource.JsonResourceContext;
import org.forgerock.json.resource.JsonResourceException;
import org.forgerock.openam.ext.cts.CoreTokenService;
import org.forgerock.openam.ext.cts.repo.OpenDJTokenRepo;
import com.sun.identity.shared.OAuth2Constants;
import org.forgerock.openam.oauth2.model.AuthorizationCode;
import org.forgerock.openam.oauth2.model.impl.AccessTokenImpl;
import org.forgerock.openam.oauth2.model.impl.AuthorizationCodeImpl;
import org.forgerock.openam.oauth2.model.impl.RefreshTokenImpl;
import org.forgerock.openam.oauth2.model.impl.SessionClientImpl;
import org.forgerock.openam.oauth2.exceptions.OAuthProblemException;
import org.forgerock.openam.oauth2.provider.OAuth2TokenStore;
import org.forgerock.openam.oauth2.utils.OAuth2Utils;
import org.restlet.Request;
import org.restlet.data.Status;

/**
 * Implementation of the OAuthTokenStore interface that uses the
 * CoreTokenService for storing the tokens as JSON objects.
 */
public class DefaultOAuthTokenStoreImpl implements OAuth2TokenStore {

    //lifetimes are in seconds
    private long AUTHZ_CODE_LIFETIME = 1;
    private long REFRESH_TOKEN_LIFETIME = 1;
    private long ACCESS_TOKEN_LIFETIME = 1;

    // Removed: long requestTime
    // Removed: String clientID
    // Removed: String redirectURI

    private JsonResource repository;

    /**
     * Constructor, creates the repository instance used.
     * 
     * @throws OAuthProblemException
     */
    public DefaultOAuthTokenStoreImpl() {
        try {
            repository = new CoreTokenService(OpenDJTokenRepo.getInstance());
        } catch (Exception e) {
            // TODO: legacy code throws Exception, look to refactor
            throw new OAuthProblemException(Status.SERVER_ERROR_SERVICE_UNAVAILABLE.getCode(),
                    "Service unavailable", "Could not create underlying storage", null);
        }
    }
    public void getSettings(String realm){
        if (realm == null){
            //default realm
            realm = "/";
        }
        try {
            SSOToken token = (SSOToken) AccessController.doPrivileged(AdminTokenAction.getInstance());
            ServiceConfigManager mgr = new ServiceConfigManager(token, OAuth2Constants.OAuth2ProviderService.NAME, OAuth2Constants.OAuth2ProviderService.VERSION);
            ServiceConfig scm = mgr.getOrganizationConfig(realm, null);
            Map<String, Set<String>> attrs = scm.getAttributes();
            AUTHZ_CODE_LIFETIME = Long.parseLong(attrs.get(OAuth2Constants.OAuth2ProviderService.AUTHZ_CODE_LIFETIME_NAME).iterator().next());
            REFRESH_TOKEN_LIFETIME = Long.parseLong(attrs.get(OAuth2Constants.OAuth2ProviderService.REFRESH_TOKEN_LIFETIME_NAME).iterator().next());
            ACCESS_TOKEN_LIFETIME = Long.parseLong(attrs.get(OAuth2Constants.OAuth2ProviderService.ACCESS_TOKEN_LIFETIME_NAME).iterator().next());
        } catch (Exception e) {
            OAuth2Utils.DEBUG.error("DefaultOAuthTokenStoreImpl::Unable to read service settings", e);
            throw OAuthProblemException.OAuthError.SERVER_ERROR.handle(null,
                    "Unable to read service settings");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public org.forgerock.openam.oauth2.model.AuthorizationCode createAuthorizationCode(Set<String> scope, String realm, String uuid,
            org.forgerock.openam.oauth2.model.SessionClient client) {
        if (OAuth2Utils.DEBUG.messageEnabled()){
            OAuth2Utils.DEBUG.message("DefaultOAuthTokenStoreImpl::Creating Authorization code");
        }
        getSettings(realm);
        String id = UUID.randomUUID().toString();
        long expiresIn = AUTHZ_CODE_LIFETIME;

        AuthorizationCodeImpl code =
                new AuthorizationCodeImpl(id, uuid, client, realm, scope, false, expiresIn);
        JsonValue response = null;

        // Store in CTS
        JsonResourceAccessor accessor =
                new JsonResourceAccessor(repository, JsonResourceContext.newRootContext());
        try {
            response = accessor.create(id, code);
        } catch (JsonResourceException e) {
            OAuth2Utils.DEBUG.error("DefaultOAuthTokenStoreImpl::Unable to create authorization code", e);
            throw new OAuthProblemException(Status.SERVER_ERROR_INTERNAL.getCode(),
                    "Internal error", "Could not create token in CTS", null);
        }

        if (response == null) {
            OAuth2Utils.DEBUG.error("DefaultOAuthTokenStoreImpl::Unable to create authorization code");
            throw new OAuthProblemException(Status.SERVER_ERROR_INTERNAL.getCode(),
                    "Internal error", "Could not create token in CTS", null);
        }

        return code;
    }

    /**
     * {@inheritDoc}
     */
    public void updateAuthorizationCode(String id, AuthorizationCode code) throws OAuthProblemException{
        deleteAuthorizationCode(id);
        JsonValue response = null;

        // Store in CTS
        JsonResourceAccessor accessor =
                new JsonResourceAccessor(repository, JsonResourceContext.newRootContext());

        AuthorizationCodeImpl code2 =
                new AuthorizationCodeImpl(id, code.getUserID(), code.getClient(), code.getRealm(),
                    code.getScope(), code.isTokenIssued(), code.getExpireTime());
        try {
            response = accessor.create(id, code2);
        } catch (JsonResourceException e) {
            OAuth2Utils.DEBUG.error("DefaultOAuthTokenStoreImpl::Unable to create authorization code", e);
            throw new OAuthProblemException(Status.SERVER_ERROR_INTERNAL.getCode(),
                    "Internal error", "Could not create token in CTS", null);
        }

        if (response == null) {
            OAuth2Utils.DEBUG.error("DefaultOAuthTokenStoreImpl::Unable to create authorization code");
            throw new OAuthProblemException(Status.SERVER_ERROR_INTERNAL.getCode(),
                    "Internal error", "Could not create token in CTS", null);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public org.forgerock.openam.oauth2.model.AuthorizationCode readAuthorizationCode(String id) {
        if (OAuth2Utils.DEBUG.messageEnabled()){
            OAuth2Utils.DEBUG.message("DefaultOAuthTokenStoreImpl::Reading Authorization code: " + id);
        }
        JsonValue response = null;

        // Read from CTS
        JsonResourceAccessor accessor =
                new JsonResourceAccessor(repository, JsonResourceContext.newRootContext());
        try {
            response = accessor.read(id);
        } catch (JsonResourceException e) {
            OAuth2Utils.DEBUG.error("DefaultOAuthTokenStoreImpl::Unable to read authorization code", e);
            throw new OAuthProblemException(Status.SERVER_ERROR_INTERNAL.getCode(),
                    "Internal error", "Could not read token from CTS: " + e.getMessage(), null);
        }

        if (response == null) {
            OAuth2Utils.DEBUG.error("DefaultOAuthTokenStoreImpl::Unable to create authorization code");
            throw new OAuthProblemException(Status.CLIENT_ERROR_NOT_FOUND.getCode(), "Not found",
                    "Could not find token from CTS", null);
        }

        org.forgerock.openam.oauth2.model.AuthorizationCode ac = new AuthorizationCodeImpl(id, response);
        return ac;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteAuthorizationCode(String id) {
        if (OAuth2Utils.DEBUG.messageEnabled()){
            OAuth2Utils.DEBUG.message("DefaultOAuthTokenStoreImpl::Deleting Authorization code: " + id);
        }
        JsonValue response = null;

        // Read from CTS
        JsonResourceAccessor accessor =
                new JsonResourceAccessor(repository, JsonResourceContext.newRootContext());
        try {
            response = accessor.read(id);
        } catch (JsonResourceException e) {
            OAuth2Utils.DEBUG.error("DefaultOAuthTokenStoreImpl::Unable to delete authorization code", e);
            throw new OAuthProblemException(Status.SERVER_ERROR_INTERNAL.getCode(),
                    "Internal error", "Could not read token from CTS: " + e.getMessage(), null);
        }

        if (response == null) {
            OAuth2Utils.DEBUG.error("DefaultOAuthTokenStoreImpl::Unable to create authorization code");
            throw new OAuthProblemException(Status.CLIENT_ERROR_NOT_FOUND.getCode(), "Not found",
                    "Could not find token using CTS", null);
        }

        // Create a query for other tokens with this as a parent
        // TODO secondary key search via query

        // Delete the code
        try {
            response = accessor.delete(id, null);
        } catch (JsonResourceException e) {
            OAuth2Utils.DEBUG.error("DefaultOAuthTokenStoreImpl::Unable to delete authorization code", e);
            throw new OAuthProblemException(Status.SERVER_ERROR_INTERNAL.getCode(),
                    "Internal error", "Could not delete token from CTS: " + e.getMessage(), null);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public org.forgerock.openam.oauth2.model.AccessToken createAccessToken(String accessTokenType, Set<String> scope,
            org.forgerock.openam.oauth2.model.AuthorizationCode code, String realm) {
        if (OAuth2Utils.DEBUG.messageEnabled()){
            OAuth2Utils.DEBUG.message("DefaultOAuthTokenStoreImpl::Creating access token");
        }
        getSettings(realm);
        JsonValue response = null;

        String id = UUID.randomUUID().toString();
        long expireTime = ACCESS_TOKEN_LIFETIME;

        AccessTokenImpl accessToken = new AccessTokenImpl(id, scope, expireTime, code);

        // Create in CTS
        JsonResourceAccessor accessor =
                new JsonResourceAccessor(repository, JsonResourceContext.newRootContext());
        try {
            response = accessor.create(id, accessToken);
        } catch (JsonResourceException e) {
            OAuth2Utils.DEBUG.error("DefaultOAuthTokenStoreImpl::Unable to create access token", e);
            throw new OAuthProblemException(Status.SERVER_ERROR_INTERNAL.getCode(),
                    "Internal error", "Could not create token in CTS: " + e.getMessage(), null);
        }

        if (response == null) {
            OAuth2Utils.DEBUG.error("DefaultOAuthTokenStoreImpl::Unable to create access token");
            throw new OAuthProblemException(Status.CLIENT_ERROR_NOT_FOUND.getCode(), "Not found",
                    "Could not create token in CTS", null);
        }

        return accessToken;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public org.forgerock.openam.oauth2.model.AccessToken createAccessToken(String accessTokenType, Set<String> scope,
            org.forgerock.openam.oauth2.model.RefreshToken refreshToken, String realm) {
        if (OAuth2Utils.DEBUG.messageEnabled()){
            OAuth2Utils.DEBUG.message("DefaultOAuthTokenStoreImpl::Creating access token");
        }
        getSettings(realm);
        JsonValue response = null;

        String id = UUID.randomUUID().toString();
        long expireTime = ACCESS_TOKEN_LIFETIME;

        AccessTokenImpl accessToken = new AccessTokenImpl(id, scope, expireTime, refreshToken);

        // Create in CTS
        JsonResourceAccessor accessor =
                new JsonResourceAccessor(repository, JsonResourceContext.newRootContext());
        try {
            response = accessor.create(id, accessToken);
        } catch (JsonResourceException e) {
            OAuth2Utils.DEBUG.error("DefaultOAuthTokenStoreImpl::Unable to create access token", e);
            throw new OAuthProblemException(Status.SERVER_ERROR_INTERNAL.getCode(),
                    "Internal error", "Could not create token in CTS: " + e.getMessage(), null);
        }

        if (response == null) {
            OAuth2Utils.DEBUG.error("DefaultOAuthTokenStoreImpl::Unable to create access token");
            throw new OAuthProblemException(Status.CLIENT_ERROR_NOT_FOUND.getCode(), "Not found",
                    "Could not create token in CTS", null);
        }

        return accessToken;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public org.forgerock.openam.oauth2.model.AccessToken createAccessToken(String accessTokenType, Set<String> scope, String realm,
            String uuid) {
        if (OAuth2Utils.DEBUG.messageEnabled()){
            OAuth2Utils.DEBUG.message("DefaultOAuthTokenStoreImpl::Creating access token");
        }
        getSettings(realm);
        JsonValue response = null;

        String id = UUID.randomUUID().toString();
        long expireTime = ACCESS_TOKEN_LIFETIME;

        AccessTokenImpl accessToken =
                new AccessTokenImpl(id, null, uuid, null, realm, scope, expireTime);

        // Create in CTS
        JsonResourceAccessor accessor =
                new JsonResourceAccessor(repository, JsonResourceContext.newRootContext());
        try {
            response = accessor.create(id, accessToken);
        } catch (JsonResourceException e) {
            OAuth2Utils.DEBUG.error("DefaultOAuthTokenStoreImpl::Unable to create access token", e);
            throw new OAuthProblemException(Status.SERVER_ERROR_INTERNAL.getCode(),
                    "Internal error", "Could not create token in CTS: " + e.getMessage(), null);
        }

        if (response == null) {
            OAuth2Utils.DEBUG.error("DefaultOAuthTokenStoreImpl::Unable to create access token");
            throw new OAuthProblemException(Status.CLIENT_ERROR_NOT_FOUND.getCode(), "Not found",
                    "Could not create token in CTS", null);
        }

        return accessToken;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public org.forgerock.openam.oauth2.model.AccessToken createAccessToken(String accessTokenType, Set<String> scope, String realm,
            String uuid, org.forgerock.openam.oauth2.model.SessionClient client) {
        if (OAuth2Utils.DEBUG.messageEnabled()){
            OAuth2Utils.DEBUG.message("DefaultOAuthTokenStoreImpl::Creating access token");
        }
        getSettings(realm);
        JsonValue response = null;

        String id = UUID.randomUUID().toString();
        long expireTime = ACCESS_TOKEN_LIFETIME;

        AccessTokenImpl accessToken =
                new AccessTokenImpl(id, null, uuid, client, realm, scope, expireTime);

        // Create in CTS
        JsonResourceAccessor accessor =
                new JsonResourceAccessor(repository, JsonResourceContext.newRootContext());
        try {
            response = accessor.create(id, accessToken);
        } catch (JsonResourceException e) {
            OAuth2Utils.DEBUG.error("DefaultOAuthTokenStoreImpl::Unable to create access token", e);
            throw new OAuthProblemException(Status.SERVER_ERROR_INTERNAL.getCode(),
                    "Internal error", "Could not create token in CTS: " + e.getMessage(), null);
        }

        if (response == null) {
            OAuth2Utils.DEBUG.error("DefaultOAuthTokenStoreImpl::Unable to create access token");
            throw new OAuthProblemException(Status.CLIENT_ERROR_NOT_FOUND.getCode(), "Not found",
                    "Could not create token in CTS", null);
        }

        return accessToken;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public org.forgerock.openam.oauth2.model.AccessToken createAccessToken(String accessTokenType, Set<String> scope, String realm,
            String uuid, String clientId, org.forgerock.openam.oauth2.model.RefreshToken refreshToken) {
        if (OAuth2Utils.DEBUG.messageEnabled()){
            OAuth2Utils.DEBUG.message("DefaultOAuthTokenStoreImpl::Creating access token");
        }
        getSettings(realm);
        JsonValue response = null;

        String id = UUID.randomUUID().toString();
        long expireTime = ACCESS_TOKEN_LIFETIME;
        AccessTokenImpl accessToken;

        if (refreshToken != null){
            accessToken =
                new AccessTokenImpl(id, refreshToken.getToken(), uuid, new SessionClientImpl(clientId, null), realm,
                        scope, expireTime);
        } else {
            accessToken =
                    new AccessTokenImpl(id, null, uuid, new SessionClientImpl(clientId, null), realm,
                            scope, expireTime);
        }

        // Create in CTS
        JsonResourceAccessor accessor =
                new JsonResourceAccessor(repository, JsonResourceContext.newRootContext());
        try {
            response = accessor.create(id, accessToken);
        } catch (JsonResourceException e) {
            OAuth2Utils.DEBUG.error("DefaultOAuthTokenStoreImpl::Unable to create access token", e);
            throw new OAuthProblemException(Status.SERVER_ERROR_INTERNAL.getCode(),
                    "Internal error", "Could not create token in CTS: " + e.getMessage(), null);
        }

        if (response == null) {
            OAuth2Utils.DEBUG.error("DefaultOAuthTokenStoreImpl::Unable to create access token");
            throw new OAuthProblemException(Status.CLIENT_ERROR_NOT_FOUND.getCode(), "Not found",
                    "Could not create token in CTS", null);
        }

        return accessToken;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public org.forgerock.openam.oauth2.model.AccessToken readAccessToken(String id) {
        if (OAuth2Utils.DEBUG.messageEnabled()){
            OAuth2Utils.DEBUG.message("DefaultOAuthTokenStoreImpl::Reading access token");
        }
        JsonValue response = null;

        // Create in CTS
        JsonResourceAccessor accessor =
                new JsonResourceAccessor(repository, JsonResourceContext.newRootContext());
        try {
            response = accessor.read(id);
        } catch (JsonResourceException e) {
            OAuth2Utils.DEBUG.error("DefaultOAuthTokenStoreImpl::Unable to read access token", e);
            throw new OAuthProblemException(Status.SERVER_ERROR_INTERNAL.getCode(),
                    "Internal error", "Could not read token in CTS: " + e.getMessage(), null);
        }

        if (response == null) {
            OAuth2Utils.DEBUG.error("DefaultOAuthTokenStoreImpl::Unable to read access token");
            throw new OAuthProblemException(Status.CLIENT_ERROR_NOT_FOUND.getCode(), "Not found",
                    "Could not read token in CTS", null);
        }

        org.forgerock.openam.oauth2.model.AccessToken accessToken = new AccessTokenImpl(id, response);
        return accessToken;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteAccessToken(String id) {
        if (OAuth2Utils.DEBUG.messageEnabled()){
            OAuth2Utils.DEBUG.message("DefaultOAuthTokenStoreImpl::Deleting access token");
        }
        JsonValue response = null;

        // Delete the code
        JsonResourceAccessor accessor =
                new JsonResourceAccessor(repository, JsonResourceContext.newRootContext());
        try {
            response = accessor.delete(id, null);
        } catch (JsonResourceException e) {
            OAuth2Utils.DEBUG.error("DefaultOAuthTokenStoreImpl::Unable to delete access token", e);
            throw new OAuthProblemException(Status.SERVER_ERROR_INTERNAL.getCode(),
                    "Internal error", "Could not delete token from CTS: " + e.getMessage(), null);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public org.forgerock.openam.oauth2.model.RefreshToken createRefreshToken(Set<String> scope, String realm, String uuid,
            String clientId, AuthorizationCode parent) {
        if (OAuth2Utils.DEBUG.messageEnabled()){
            OAuth2Utils.DEBUG.message("DefaultOAuthTokenStoreImpl::Create refresh token");
        }
        getSettings(realm);
        JsonValue response = null;

        String id = UUID.randomUUID().toString();
        long expireTime = REFRESH_TOKEN_LIFETIME;
        RefreshTokenImpl refreshToken = null;
        if (parent != null){
            refreshToken =
                new RefreshTokenImpl(id, parent.getToken(), uuid, new SessionClientImpl(clientId, null), realm, scope, expireTime);
        } else {
            refreshToken =
                    new RefreshTokenImpl(id, null, uuid, new SessionClientImpl(clientId, null), realm, scope, expireTime);
        }

        // Create in CTS
        JsonResourceAccessor accessor =
                new JsonResourceAccessor(repository, JsonResourceContext.newRootContext());
        try {
            response = accessor.create(id, refreshToken);
        } catch (JsonResourceException e) {
            OAuth2Utils.DEBUG.error("DefaultOAuthTokenStoreImpl::Unable to create refresh token", e);
            throw new OAuthProblemException(Status.SERVER_ERROR_INTERNAL.getCode(),
                    "Internal error", "Could not create token in CTS: " + e.getMessage(), null);
        }

        if (response == null) {
            OAuth2Utils.DEBUG.error("DefaultOAuthTokenStoreImpl::Unable to create refresh token");
            throw new OAuthProblemException(Status.CLIENT_ERROR_NOT_FOUND.getCode(), "Not found",
                    "Could not create token in CTS", null);
        }

        return refreshToken;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public org.forgerock.openam.oauth2.model.RefreshToken readRefreshToken(String id) {
        if (OAuth2Utils.DEBUG.messageEnabled()){
            OAuth2Utils.DEBUG.message("DefaultOAuthTokenStoreImpl::Read refresh token");
        }
        JsonValue response = null;

        // Read from CTS
        JsonResourceAccessor accessor =
                new JsonResourceAccessor(repository, JsonResourceContext.newRootContext());
        try {
            response = accessor.read(id);
        } catch (JsonResourceException e) {
            OAuth2Utils.DEBUG.error("DefaultOAuthTokenStoreImpl::Unable to read refresh token", e);
            throw OAuthProblemException.OAuthError.INVALID_REQUEST.handle(Request.getCurrent());
        }

        if (response == null) {
            OAuth2Utils.DEBUG.error("DefaultOAuthTokenStoreImpl::Unable to read refresh token");
            throw new OAuthProblemException(Status.CLIENT_ERROR_NOT_FOUND.getCode(), "Not found",
                    "Could not find token from CTS", null);
        }

        org.forgerock.openam.oauth2.model.RefreshToken rt = new RefreshTokenImpl(id, response);
        return rt;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteRefreshToken(String id) {
        JsonValue response = null;

        // Delete the code
        JsonResourceAccessor accessor =
                new JsonResourceAccessor(repository, JsonResourceContext.newRootContext());
        try {
            response = accessor.delete(id, null);
        } catch (JsonResourceException e) {
            OAuth2Utils.DEBUG.error("DefaultOAuthTokenStoreImpl::Unable to delete refresh token", e);
            throw OAuthProblemException.OAuthError.INVALID_REQUEST.handle(Request.getCurrent());
        }

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public JsonValue queryForToken(String id) throws OAuthProblemException{
        JsonValue response = null;

        // Delete the code
        JsonResourceAccessor accessor =
                new JsonResourceAccessor(repository, JsonResourceContext.newRootContext());

        //construct the filter
        Map query = new HashMap<String,String>();
        query.put("parent", id);
        JsonValue queryFilter = new JsonValue(new HashMap<String, HashMap<String, String>>());
        if (query != null){
            queryFilter.put("filter", query);
        }

        try {
            response = accessor.query(id, queryFilter);
        } catch (JsonResourceException e) {
            OAuth2Utils.DEBUG.error("DefaultOAuthTokenStoreImpl::Unable to delete refresh token", e);
            throw OAuthProblemException.OAuthError.INVALID_REQUEST.handle(Request.getCurrent());
        }

        return response;
    }

}
