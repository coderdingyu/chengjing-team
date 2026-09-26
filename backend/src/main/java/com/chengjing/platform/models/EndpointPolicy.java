package com.chengjing.platform.models;

import com.chengjing.platform.PlatformException;
import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Checks compatible API roots and rejects private-network destinations by default. */
@Component
public class EndpointPolicy {
    private final boolean allowLocal;

    public EndpointPolicy(@Value("${chengjing.models.allow-local:false}") boolean allowLocal) {
        this.allowLocal = allowLocal;
    }

    public String normalize(String raw) {
        try {
            if (raw == null || raw.isBlank() || raw.length() > 500) throw new IllegalArgumentException();
            URI uri = URI.create(raw.strip().replaceAll("/+$", ""));
            String host = uri.getHost();
            if (host == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || uri.getPath().contains("..") || uri.getPath().endsWith("/chat/completions")) throw new IllegalArgumentException();
            boolean local = Set.of("localhost", "127.0.0.1", "::1", "[::1]").contains(host.toLowerCase(Locale.ROOT));
            if (!"https".equals(uri.getScheme()) && !(allowLocal && local && "http".equals(uri.getScheme())))
                throw new IllegalArgumentException();
            if ((local && !allowLocal) || host.toLowerCase(Locale.ROOT).endsWith(".local")) throw new IllegalArgumentException();
            return uri.toASCIIString();
        } catch (Exception e) {
            throw new PlatformException(HttpStatus.BAD_REQUEST, "填写 HTTPS 接口根地址；本机 HTTP 仅在显式启用后可用");
        }
    }

    /** Recheck DNS immediately before connecting. The HTTP client must not follow redirects. */
    public void checkDestination(String url) {
        URI uri = URI.create(normalize(url));
        String host = uri.getHost();
        boolean local = Set.of("localhost", "127.0.0.1", "::1", "[::1]").contains(host.toLowerCase(Locale.ROOT));
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                byte[] bytes = address.getAddress();
                boolean privateIpv6 = bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
                boolean carrierNat = bytes.length == 4 && (bytes[0] & 255) == 100 && ((bytes[1] & 255) & 192) == 64;
                if (address.isAnyLocalAddress() || address.isMulticastAddress() || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress() || privateIpv6 || carrierNat
                        || (address.isLoopbackAddress() && !(allowLocal && local)))
                    throw new IllegalArgumentException();
            }
        } catch (Exception e) {
            throw new PlatformException(HttpStatus.BAD_REQUEST, "模型站点地址无法连接或指向未允许的内网地址");
        }
    }
}
