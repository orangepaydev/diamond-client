function FindProxyForURL(url, host) {

// If the hostname matches, send direct.
    if (dnsDomainIs(host, "artifactory") ||
    dnsDomainIs(host, "bitbucket") ||
    dnsDomainIs(host, "jira") )
        return "PROXY localhost:3168";

    return "DIRECT";
}