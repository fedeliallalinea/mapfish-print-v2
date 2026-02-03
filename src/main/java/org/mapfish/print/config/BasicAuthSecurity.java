package org.mapfish.print.config;

import java.net.URI;

import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.auth.AuthCache;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.impl.auth.BasicAuthCache;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.auth.BasicScheme;

public class BasicAuthSecurity extends SecurityStrategy {

    String username = null;
    String password = null;
    boolean preemptive = false;

    @Override
    public HttpClientContext createContext(URI uri){
        if(username==null || password==null) throw new IllegalStateException("username and password configuration of BasicAuthSecurity is required");

        BasicCredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(
                new AuthScope(uri.getHost(), uri.getPort()),
                new UsernamePasswordCredentials(username, password.toCharArray())
        );
        HttpClientContext context = HttpClientContext.create();
        context.setCredentialsProvider(credsProvider);

        if (preemptive) {
            HttpHost target = new HttpHost(uri.getScheme(), uri.getHost(), uri.getPort());
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
