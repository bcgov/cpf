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
package ca.bc.gov.open.cpf.api.worker;

import java.util.Collections;
import java.util.Map;

import ca.bc.gov.open.cpf.plugin.impl.module.Module;
import ca.bc.gov.open.cpf.plugin.impl.security.AbstractCachingSecurityService;

import com.revolsys.collection.map.LinkedHashMapEx;
import com.revolsys.collection.map.MapEx;
import com.revolsys.websocket.json.JsonAsyncSender;
import com.revolsys.websocket.json.JsonPropertyAsyncResult;

public class WorkerSecurityService extends AbstractCachingSecurityService {
  private final WorkerMessageHandler messageHandler;

  private final String moduleName;

  public WorkerSecurityService(final WorkerMessageHandler messageHandler, final Module module,
    final String userId) {
    super(module, userId);
    this.messageHandler = messageHandler;
    this.moduleName = module.getName();
  }

  @Override
  protected Boolean loadActionPermission(final String actionName) {
    try {
      final JsonAsyncSender messageSender = this.messageHandler.getMessageSender();
      if (messageSender == null) {
        return false;
      } else {
        final MapEx message = new LinkedHashMapEx("type", "securityCanAccessResource");
        message.put("moduleName", this.moduleName);
        message.put("consumerKey", getUsername());
        message.put("actionName", actionName);
        return messageSender.sendAndWait(message, new JsonPropertyAsyncResult("hasAccess"));
      }
    } catch (final Throwable e) {
      throw new RuntimeException(
        "Unable to get security action permission for " + getUsername() + " action=" + actionName,
        e);
    }
  }

  @Override
  protected Boolean loadGroupPermission(final String groupName) {
    try {
      final JsonAsyncSender messageSender = this.messageHandler.getMessageSender();
      if (messageSender == null) {
        return false;
      } else {
        final MapEx message = new LinkedHashMapEx("type", "securityIsMemberOfGroup");
        message.put("moduleName", this.moduleName);
        message.put("consumerKey", getUsername());
        message.put("groupName", groupName);
        return messageSender.sendAndWait(message, new JsonPropertyAsyncResult("memberOfGroup"));
      }
    } catch (final Throwable e) {
      throw new RuntimeException(
        "Unable to get security group permission for " + getUsername() + " group=" + groupName, e);
    }
  }

  @Override
  protected Boolean loadResourceAccessPermission(final String resourceClass,
    final String resourceId, final String actionName) {
    try {
      final JsonAsyncSender messageSender = this.messageHandler.getMessageSender();
      if (messageSender == null) {
        return false;
      } else {
        final MapEx message = new LinkedHashMapEx("type", "securityCanAccessResource");
        message.put("moduleName", this.moduleName);
        message.put("consumerKey", getUsername());
        message.put("resourceClass", resourceClass);
        message.put("resourceId", resourceId);
        message.put("actionName", actionName);
        return messageSender.sendAndWait(message, new JsonPropertyAsyncResult("hasAccess"));
      }
    } catch (final Throwable e) {
      throw new RuntimeException("Unable to get security resource access permission for "
        + getUsername() + " resourcesClass=" + resourceClass + " resourceId=" + resourceId
        + " action=" + actionName, e);
    }
  }

  @Override
  protected Map<String, Object> loadUserAttributes() {
    try {
      final JsonAsyncSender messageSender = this.messageHandler.getMessageSender();
      if (messageSender == null) {
        return Collections.emptyMap();
      } else {
        final MapEx message = new LinkedHashMapEx("type", "securityCanAccessResource");
        message.put("moduleName", this.moduleName);
        message.put("consumerKey", getUsername());
        return messageSender.sendAndWait(message, new JsonPropertyAsyncResult("attributes"));
      }
    } catch (final Throwable e) {
      throw new RuntimeException("Unable to get user attributes for " + getUsername(), e);
    }
  }
}
