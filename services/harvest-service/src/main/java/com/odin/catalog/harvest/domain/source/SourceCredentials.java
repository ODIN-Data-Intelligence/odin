package com.odin.catalog.harvest.domain.source;

public record SourceCredentials(
    String credentialType,    // access_key, oauth_token, jdbc_password, bearer_token
    String accessKeyId,
    String secretAccessKey,
    String username,
    String password,
    String bearerToken,
    String oauthClientId,
    String oauthClientSecret,
    String oauthTokenUrl
) {}
