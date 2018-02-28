/*
 * Copyright © 2008-2016, Province of British Columbia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ca.bc.gov.open.cpf.client.httpclient;

import java.util.HashSet;
import java.util.Set;

@SuppressWarnings("javadoc")
public class CpfHttpClientPool {

  private Set<CpfHttpClient> clients = new HashSet<>();

  private String consumerKey;

  private String consumerSecret;

  private int maxConnections = 10;

  private String webServiceUrl;

  public CpfHttpClientPool() {
  }

  public CpfHttpClientPool(final String webServiceUrl, final String consumerKey,
    final String consumerSecret, final int maxConnections) {
    this.webServiceUrl = webServiceUrl;
    this.consumerKey = consumerKey;
    this.consumerSecret = consumerSecret;
    this.maxConnections = maxConnections;
  }

  public void close() {
    synchronized (this.clients) {
      for (final CpfHttpClient client : this.clients) {
        client.close();
      }
      final Set<CpfHttpClient> oldClients = this.clients;
      this.clients = null;
      oldClients.notifyAll();
    }
  }

  public CpfHttpClient getClient() {
    synchronized (this.clients) {
      while (this.clients != null && this.clients.size() >= this.maxConnections) {
        try {
          this.clients.wait();
        } catch (final InterruptedException e) {
        }
      }
      if (this.clients == null) {
        throw new IllegalStateException("Connection pool closed");
      } else {
        final CpfHttpClient client = new CpfHttpClient(this, this.webServiceUrl, this.consumerKey,
          this.consumerSecret);
        return client;
      }
    }
  }

  public String getConsumerKey() {
    return this.consumerKey;
  }

  public String getConsumerSecret() {
    return this.consumerSecret;
  }

  public int getMaxConnections() {
    return this.maxConnections;
  }

  public String getWebServiceUrl() {
    return this.webServiceUrl;
  }

  public void releaseClient(final CpfHttpClient client) {
    synchronized (this.clients) {
      if (client != null) {
        client.getHttpClient().getConnectionManager().shutdown();
        if (this.clients != null) {
          this.clients.remove(client);
          this.clients.notifyAll();
        }
      }
    }
  }

  public void setConsumerKey(final String consumerKey) {
    this.consumerKey = consumerKey;
  }

  public void setConsumerSecret(final String consumerSecret) {
    this.consumerSecret = consumerSecret;
  }

  public void setMaxConnections(final int maxConnections) {
    synchronized (this.clients) {
      this.maxConnections = maxConnections;
      this.clients.notifyAll();
    }
  }

  public void setWebServiceUrl(final String webServiceUrl) {
    this.webServiceUrl = webServiceUrl;
  }
}
