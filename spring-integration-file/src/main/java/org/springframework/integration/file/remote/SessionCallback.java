/*
 * Copyright 2013-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.file.remote;

import java.io.IOException;

import org.springframework.integration.file.remote.session.Session;

/**
 * Callback invoked by {@code RemoteFileOperations.execute()} - allows multiple operations
 * on a session.
 *
 * @author Gary Russell
 * @since 3.0
 *
 */
public interface SessionCallback<F, T> {

	/**
	 * Called within the context of a session.
	 * Perform some operation(s) on the session. The caller will take
	 * care of closing the session after this method exits.
	 *
	 * @param session The session.
	 * @return The result of type T.
	 * @throws IOException Any IOException.
	 */
	T doInSession(Session<F> session) throws IOException;

}
