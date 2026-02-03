/*
 * Copyright (C) 2013  Camptocamp
 *
 * This file is part of MapFish Print
 *
 * MapFish Print is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MapFish Print is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MapFish Print.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.mapfish.print.config;

import org.apache.hc.client5.http.auth.AuthCache;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.util.Timeout;
import org.json.JSONWriter;
import org.junit.Before;
import org.junit.Test;
import org.mapfish.print.PrintTestCase;
import org.mapfish.print.ShellMapPrinter;
import org.mapfish.print.ThreadResources;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class ConfigTest extends PrintTestCase {

    public static final String GEORCHESTRA_YAML = "config-georchestra.yaml";

    public static final String CONFIG_TEST_CLASS_FILE = ConfigTest.class.getResource(ConfigTest.class.getSimpleName() + ".class"
            ).getFile();

    public static final File CONFIG_TEST_CLASS_DIR = new File(CONFIG_TEST_CLASS_FILE).getParentFile();

    private ThreadResources threadResources;

    @Before
    public void setup() throws Exception {
        threadResources = new ThreadResources();
        threadResources.init();
    }

    @Test
    public void testParse() throws FileNotFoundException {
        new ClassPathXmlApplicationContext(ShellMapPrinter.DEFAULT_SPRING_CONTEXT).getBean(ConfigFactory.class).fromYaml(new File("samples/config.yaml"));
    }

    @Test
    public void testBestScale() {
        Config config = new Config();
        try {
            TreeSet<Number> scales = new TreeSet<Number>(Arrays.asList(200000.0, 25000.0, 50000.0, 100000.0));
            config.setScales(scales);

            assertEquals("Too small scale => pick the smallest available", 25000, config.getBestScale(1), 0.000001);
            assertEquals("Exact match", 25000, config.getBestScale(25000.0), 0.000001);
            assertEquals("Just too big => should still take the previous one", 25000, config.getBestScale(25000.1), 0.000001);
            assertEquals("Normal behaviour", 200000, config.getBestScale(150000), 0.000001);
            assertEquals("Just a litle before", 200000, config.getBestScale(199999.9), 0.000001);
            assertEquals("When we want a scale that is too big, pick the highest available", 200000, config.getBestScale(99999999999.0), 0.000001);
        } finally {
            config.close();
        }
    }

    @Test
    public void testReadExampleConfigFiles() throws Exception {
        Map<String, File> filesInSamplesDir = getSampleConfigFiles();
        StringBuilder errorString = new StringBuilder("The following errors occurred while parsing yaml config files: \n");
        boolean error = false;
        try {
            final ConfigFactory configFactory = new ConfigFactory(threadResources);
            for (File file : filesInSamplesDir.values()) {
                if (file.getName().endsWith(".yaml")) {
                    try {
                        final Config config = configFactory.fromYaml(file);
                        config.getBestScale(1000);
                        JSONWriter json = new JSONWriter(new StringWriter());
                        json.object();
                        config.printClientConfig(json);

                        if (!config.getDpis().isEmpty()) {
                            final Integer next = config.getDpis().iterator().next();
                            assertNotNull(next);
                        }
                    } catch (Throwable e) {
                        error = true;
                        StringWriter trace = new StringWriter();
                        e.printStackTrace(new PrintWriter(trace));
                        errorString.append("\t" + file.getPath() + ": " + e.getMessage() + "\n");
                        errorString.append(trace.toString().replace("\n", "\n\t\t") + "\n\n");
                    }
                }
            }

            assertFalse(errorString.toString(), error);
        } finally {
            threadResources.destroy();
        }

    }

    @Test
    public void testLargeConfigFile() throws Exception {
        File configFile = getConfigLarge();
        StringBuilder errorString = new StringBuilder("The following errors occurred while parsing yaml config file: \n");
        boolean error = false;
        try {
            final ConfigFactory configFactory = new ConfigFactory(threadResources);
            try {
                final Config config = configFactory.fromYaml(configFile);
                config.getBestScale(1000);
                JSONWriter json = new JSONWriter(new StringWriter());
                json.object();
                config.printClientConfig(json);

                if (!config.getDpis().isEmpty()) {
                    final Integer next = config.getDpis().iterator().next();
                    assertNotNull(next);
                }
            } catch (Throwable e) {
                error = true;
                StringWriter trace = new StringWriter();
                e.printStackTrace(new PrintWriter(trace));
                errorString.append("\t" + configFile.getPath() + ": " + e.getMessage() + "\n");
                errorString.append(trace.toString().replace("\n", "\n\t\t") + "\n\n");
            }
            assertFalse(errorString.toString(), error);
        } finally {
            threadResources.destroy();
        }

    }

    @Test
    public void testCreateRequestConfig() throws Exception{
        final Config config = loadSampleConfig();
        final var uri = new java.net.URI("http://c2cpc61.camptocamp.com");

        // Save original proxy selector
        ProxySelector originalProxySelector = ProxySelector.getDefault();

        try {
            ProxySelector.setDefault(new ProxySelector() {
                @Override
                public List<Proxy> select(URI uri) {
                    return Arrays.asList(new Proxy(Proxy.Type.HTTP, new java.net.InetSocketAddress("mapfishprint.org", 8080)));
                }

                @Override
                public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                    // do nothing
                }
            });
            final RequestConfig requestConfig = config.createRequestConfig(uri);
            assertNotNull(requestConfig);
            assertEquals(Timeout.ofMilliseconds(2400000), requestConfig.getConnectionRequestTimeout());
            assertEquals(Timeout.ofMilliseconds(2400000), requestConfig.getResponseTimeout());
            assertEquals("mapfishprint.org", requestConfig.getProxy().getHostName());
            assertEquals(8080, requestConfig.getProxy().getPort());
        } finally {
            // Restore original proxy selector to avoid affecting other tests
            ProxySelector.setDefault(originalProxySelector);
        }
    }

    @Test
    public void testGetHttpClientContext() throws Exception {
        final Config config = loadSampleConfig();
        final var uri = new java.net.URI("http://c2cpc61.camptocamp.com");
        final HttpClientContext ctx = config.getHttpClientContext(uri);
        assertNotNull(ctx);
        final var credentials = (UsernamePasswordCredentials) ctx.getCredentialsProvider().getCredentials(
                new AuthScope(uri.getHost(), uri.getPort()), ctx
        );
        assertEquals("xyz", credentials.getUserName());
        assertEquals("yxz", String.valueOf(credentials.getUserPassword()));
    }

    @Test
    public void testGetHttpClientContext_casePreemptiveTrue() throws Exception {
        final Config config = loadSampleConfig();
        final var uri = new java.net.URI("http://c2cpc61.camptocamp.com");
        final HttpClientContext ctx = config.getHttpClientContext(uri);
        assertNotNull(ctx);
        final AuthCache authCache = config.getHttpClientContext(uri).getAuthCache();
        assertNotNull(authCache);
        assertNotNull(authCache.get(new HttpHost(uri.getHost())));
    }

    @Test
    public void testGetHttpClientContext_casePreemptiveFalse() throws Exception {
        final Config config = loadSampleConfig();
        final var uri = new java.net.URI("http://c2cpc42.camptocamp.com");
        final HttpClientContext ctx = config.getHttpClientContext(uri);
        assertNotNull(ctx);
        assertNull(ctx.getAuthCache());
    }

    public static Map<String, File> getSampleConfigFiles() {
        final File[] sample_config_yamls = new File(CONFIG_TEST_CLASS_DIR, "sample_config_yaml").listFiles();
        Map<String, File> nameToFileMap = new HashMap<String, File>();

        for (File file : sample_config_yamls) {
            nameToFileMap.put(file.getName(), file);
        }
        return nameToFileMap;
    }

    public static File getConfigLarge() {
        return new File(CONFIG_TEST_CLASS_DIR, "sample_config_yaml" + File.separator + "specific_samples" + File.separator + "configLarge.yaml");
    }

    public Config loadSampleConfig() {
        final var configFactory = new ConfigFactory(threadResources);

        final File configFile = new File(CONFIG_TEST_CLASS_DIR, "sample_config_yaml" + File.separator + "config.yaml");
        return configFactory.fromYaml(configFile);
    }
}
