package org.mapfish.print;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.mapfish.print.map.MapTileTask;
import org.pvalsecc.concurrent.OrderedResultsExecutor;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Encapsulates resources that start and stop threads and need to be disposed and controlled.
 *
 * @author Jesse on 5/13/2014.
 */
public class ThreadResources {

    /**
     * The bunch of threads that will be used to do the // fetching of the map
     * chunks
     */
    private OrderedResultsExecutor<MapTileTask> mapRenderingExecutor = null;

    private PoolingHttpClientConnectionManager connectionManager;

    private int perHostParallelFetches = 10;
    private int globalParallelFetches = 30;
    private int connectionTimeout = 30000;
    private int socketTimeout = 30000;

    @PostConstruct
    public void init() {
        final ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(connectionTimeout))
                .setSocketTimeout(Timeout.ofMilliseconds(socketTimeout))
                .build();
        this.connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setDefaultConnectionConfig(connectionConfig)
                .setMaxConnPerRoute(perHostParallelFetches)
                .setMaxConnTotal(globalParallelFetches)
                .build();

        mapRenderingExecutor = new OrderedResultsExecutor<MapTileTask>(globalParallelFetches, "tilesReader");
        mapRenderingExecutor.start();
    }

    @PreDestroy
    public void destroy() {
        try {
            this.connectionManager.close();
        } finally {
            this.mapRenderingExecutor.stop();
        }
    }

    public void setPerHostParallelFetches(int perHostParallelFetches) {
        this.perHostParallelFetches = perHostParallelFetches;
    }

    public void setGlobalParallelFetches(int globalParallelFetches) {
        this.globalParallelFetches = globalParallelFetches;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public void setSocketTimeout(int socketTimeout) {
        this.socketTimeout = socketTimeout;
    }

    public PoolingHttpClientConnectionManager getConnectionManager() {
        return connectionManager;
    }

    public OrderedResultsExecutor<MapTileTask> getMapRenderingExecutor() {
        return mapRenderingExecutor;
    }
}
