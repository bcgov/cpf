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
package ca.bc.gov.open.cpf.api.scheduler;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import javax.websocket.Session;

import com.revolsys.collection.map.LinkedHashMapEx;
import com.revolsys.collection.map.MapEx;
import com.revolsys.collection.map.Maps;
import com.revolsys.identifier.Identifier;
import com.revolsys.util.Property;
import com.revolsys.websocket.json.JsonAsyncSender;

public class Worker {
  private final Map<String, BatchJobRequestExecutionGroup> executingGroupsById = new TreeMap<>();

  private final Map<String, Set<BatchJobRequestExecutionGroup>> executingGroupsIdByModule = new TreeMap<>();

  private final String id;

  private Timestamp lastConnectTime;

  private final Map<String, WorkerModuleState> moduleStates = new TreeMap<>();

  private Session session;

  private final long startTime;

  private JsonAsyncSender messageSender;

  private final String key;

  public Worker(final String id, final long startTime) {
    this.id = id;
    this.key = id.toLowerCase().replaceAll("[^0-9a-z]+", "_");
    this.startTime = startTime;
  }

  public void addExecutingGroup(final String moduleName, final long moduleStartTime,
    final BatchJobRequestExecutionGroup group) {
    synchronized (this.executingGroupsById) {
      final String groupId = group.getBaseId();
      this.executingGroupsById.put(groupId, group);
      group.setModuleStartTime(moduleStartTime);
      final String moduleNameAndTime = moduleName + ":" + moduleStartTime;
      Maps.addToSet(this.executingGroupsIdByModule, moduleNameAndTime, group);
    }
  }

  public boolean cancelBatchJob(final Identifier batchJobId) {
    synchronized (this.executingGroupsById) {
      boolean found = false;
      for (final Entry<String, BatchJobRequestExecutionGroup> entry : this.executingGroupsById
        .entrySet()) {
        final String groupId = entry.getKey();
        final BatchJobRequestExecutionGroup group = entry.getValue();
        if (group.getBatchJobId().equals(batchJobId)) {
          found = true;
          final MapEx message = new LinkedHashMapEx();
          message.put("type", "cancelGroup");
          message.put("batchJobId", batchJobId);
          message.put("groupId", groupId);
          sendMessage(message);
        }
      }
      return found;
    }
  }

  public Set<BatchJobRequestExecutionGroup> cancelExecutingGroups(final String moduleNameAndTime) {
    synchronized (this.executingGroupsById) {
      final Set<BatchJobRequestExecutionGroup> groups = this.executingGroupsIdByModule
        .remove(moduleNameAndTime);
      if (groups != null) {
        for (final BatchJobRequestExecutionGroup group : groups) {
          final String groupId = group.getBaseId();
          this.executingGroupsById.remove(groupId);
        }
      }
      return groups;
    }
  }

  public BatchJobRequestExecutionGroup getExecutingGroup(final String groupId) {
    final String[] ids = groupId.split("-");
    final String baseId = ids[0] + "-" + ids[1];
    return this.executingGroupsById.get(baseId);
  }

  public List<BatchJobRequestExecutionGroup> getExecutingGroups() {
    synchronized (this.executingGroupsById) {
      return new ArrayList<>(this.executingGroupsById.values());
    }
  }

  public Map<String, BatchJobRequestExecutionGroup> getExecutingGroupsById() {
    return this.executingGroupsById;
  }

  public String getId() {
    return this.id;
  }

  public String getKey() {
    return this.key;
  }

  public Timestamp getLastConnectTime() {
    return this.lastConnectTime;
  }

  public List<WorkerModuleState> getModules() {
    return new ArrayList<>(this.moduleStates.values());
  }

  public WorkerModuleState getModuleState(final String moduleName) {
    if (Property.hasValue(moduleName)) {
      synchronized (this.moduleStates) {
        WorkerModuleState moduleState = this.moduleStates.get(moduleName);
        if (moduleState == null) {
          moduleState = new WorkerModuleState(moduleName);
          this.moduleStates.put(moduleName, moduleState);
        }
        return moduleState;
      }
    } else {
      return null;
    }
  }

  public Session getSession() {
    return this.session;
  }

  public long getStartTime() {
    return this.startTime;
  }

  public BatchJobRequestExecutionGroup removeExecutingGroup(final String groupId) {
    synchronized (this.executingGroupsById) {
      final String[] ids = groupId.split("-");
      final String baseId = ids[0] + "-" + ids[1];
      final BatchJobRequestExecutionGroup group = this.executingGroupsById.remove(baseId);
      if (group != null) {
        final String moduleName = group.getModuleName();
        final long moduleStartTime = group.getModuleStartTime();
        final String moduleNameAndTime = moduleName + ":" + moduleStartTime;

        Maps.removeFromCollection(this.executingGroupsIdByModule, moduleNameAndTime, group);
      }
      return group;
    }
  }

  public synchronized void sendMessage(final MapEx message) {
    final JsonAsyncSender sender = this.messageSender;
    if (sender != null) {
      sender.sendMessage(message);
    }
  }

  public void setLastConnectTime(final Timestamp lastConnectTime) {
    this.lastConnectTime = lastConnectTime;
  }

  public boolean setMessageResult(final MapEx message) {
    final JsonAsyncSender messageSender = this.messageSender;
    if (messageSender != null) {
      return messageSender.setResult(message);
    }
    return false;
  }

  public synchronized void setSession(final Session session) {
    this.session = session;
    final JsonAsyncSender messageSender = this.messageSender;
    if (session == null) {
      this.messageSender = null;
    } else {
      this.messageSender = new JsonAsyncSender(session);
    }
    if (messageSender != null) {
      messageSender.close();
    }
  }

  @Override
  public String toString() {
    return getId();
  }
}
