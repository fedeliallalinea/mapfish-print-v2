package org.mapfish.print.config;

import java.io.IOException;
import java.net.URI;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.apache.http.client.protocol.HttpClientContext;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = BasicAuthSecurity.class, name = "basicAuth")
})
public abstract class SecurityStrategy {
    private HostMatcher matcher;

    public abstract HttpClientContext createContext(URI uri);
    public boolean matches(URI uri) {
        try {
            return matcher==null || matcher.validate(uri);
        } catch (IOException e) {
            return false;
        }
    }

    public void setMatcher(HostMatcher matcher) {
        this.matcher = matcher;
    }



}
