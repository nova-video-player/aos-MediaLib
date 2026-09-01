// Copyright 2026 Courville Software
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.archos.mediacenter.filecoreextension.upnp2;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

import javax.servlet.Servlet;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.jupnp.transport.spi.ServletContainerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Nova-specific Jetty servlet container that adds backoff on accept() failures.
 * <p>
 * On some Android devices, {@code ServerSocketChannelImpl.accept0()} can throw
 * {@code IOException: Invalid argument} in a tight loop, flooding logcat with
 * thousands of error lines per second. This implementation overrides the
 * {@link ServerConnector#accept(int)} method to sleep 1 second before retrying
 * on failure, preventing the log storm.
 * </p>
 */
public class NovaJettyServletContainer implements ServletContainerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(NovaJettyServletContainer.class);

    public static final NovaJettyServletContainer INSTANCE = new NovaJettyServletContainer();

    protected Server server;

    private NovaJettyServletContainer() {
        resetServer();
    }

    @Override
    public synchronized void setExecutorService(ExecutorService executorService) {
        // the Jetty server has its own QueuedThreadPool
    }

    @Override
    public synchronized int addConnector(String host, int port) throws IOException {
        ServerConnector connector = new ServerConnector(server) {
            private static final long BACKOFF_MS = 1000;
            private static final long LOG_INTERVAL_MS = 60000;
            private final AtomicLong lastLogTime = new AtomicLong(0);
            private final AtomicLong failCount = new AtomicLong(0);

            @Override
            public void accept(int acceptorID) throws IOException {
                while (true) {
                    try {
                        super.accept(acceptorID);
                        // Reset on success
                        failCount.set(0);
                        return;
                    } catch (ClosedChannelException e) {
                        throw e;
                    } catch (IOException e) {
                        long count = failCount.incrementAndGet();
                        long now = System.currentTimeMillis();
                        long lastLog = lastLogTime.get();
                        if (count == 1 || now - lastLog >= LOG_INTERVAL_MS) {
                            lastLogTime.set(now);
                            logger.warn("accept() failed ({} failures since last log): {}",
                                    count, e.getMessage());
                            failCount.set(0);
                        }
                        try {
                            Thread.sleep(BACKOFF_MS);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw e;
                        }
                    }
                }
            }
        };
        connector.setHost(host);
        connector.setPort(port);

        // Open immediately so we can get the assigned local port
        connector.open();

        // Only add if open() succeeded
        server.addConnector(connector);

        // starts the connector if the server is started (server starts all connectors when started)
        if (server.isStarted()) {
            try {
                connector.start();
            } catch (Exception e) {
                logger.warn("Couldn't start connector: {}", connector, e);
                throw new RuntimeException("Couldn't start connector", e);
            }
        }
        return connector.getLocalPort();
    }

    @Override
    public synchronized void registerServlet(String contextPath, Servlet servlet) {
        if (server.getHandler() != null) {
            logger.trace("Server handler is already set: {}", server.getHandler());
            return;
        }
        logger.info("Registering UPnP servlet under context path: {}", contextPath);
        ServletContextHandler servletHandler = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        if (contextPath != null && !contextPath.isEmpty()) {
            servletHandler.setContextPath(contextPath);
        }
        final ServletHolder s = new ServletHolder(servlet);
        servletHandler.addServlet(s, "/*");
        server.setHandler(servletHandler);
    }

    @Override
    public synchronized void startIfNotRunning() {
        if (!server.isStarted() && !server.isStarting()) {
            logger.info("Starting Jetty server... ");
            try {
                server.start();
            } catch (Exception e) {
                logger.error("Couldn't start Jetty server", e);
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public synchronized void stopIfRunning() {
        if (!server.isStopped() && !server.isStopping()) {
            logger.info("Stopping Jetty server...");
            try {
                server.stop();
            } catch (Exception e) {
                logger.error("Couldn't stop Jetty server", e);
                throw new RuntimeException(e);
            } finally {
                resetServer();
            }
        }
    }

    protected void resetServer() {
        server = new Server(); // Has its own QueuedThreadPool
    }
}
