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
package ca.bc.gov.open.cpf.client.api;

import java.util.function.Consumer;

/**
 * <p>The callback interface is used by the CpfClient API to invoke code in a client application
 * to process a objects returned from the CPF REST API.</p>
 *
 * <p>The use of a callback mechanism ensures that any connections to the server are closed
 * correctly after processing a request. The connections are closed even if there was a
 * communications error or an exception thrown by the callback.</p>
 *
 * <p>A callback can also used to reduce the memory overhead of a client application. The CpfClient
 * reads the each result from the server and calls the process method for each object in turn. Keeping
 * only one result in memory at a time.</p>
 *
 * @param <T> The Java class of the object to be processed by the callback.
 */
@FunctionalInterface
public interface Callback<T> extends Consumer<T> {
  @Override
  default void accept(final T object) {
    process(object);
  }

  /**
   * Process the object.
   *
   * @param object The object to process.
   */
  void process(T object);
}
