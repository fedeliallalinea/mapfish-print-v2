package org.mapfish.print.config;

import java.net.URI;

import org.apache.http.HttpHost;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.auth.AuthScope;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;

public class BasicAuthSecurity extends SecurityStrategy {

    String username = null;
    String password = null;
    boolean preemptive = false;

    @Override
    public HttpClientContext createContext(URI uri){
        if(username==null || password==null) throw new IllegalStateException("username and password configuration of BasicAuthSecurity is required");

        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(
                new AuthScope(uri.getHost(), uri.getPort()),
                new UsernamePasswordCredentials(username, password)
        );
        HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(credsProvider);

        if (preemptive) {
            HttpHost target = new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());
            AuthCache authCache = new BasicAuthCache();
            authCache.put(target, new BasicScheme());
            context.setAuthCache(authCache);
        }

        return context;
    }

    public void setUsername(String username) {
        this.username = username;
    }
    public void setPassword(String password) {
        this.password = password;
    }
    public void setPreemptive(boolean preemptive) {
        this.preemptive = preemptive;
    }
}
