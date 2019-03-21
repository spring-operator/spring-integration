/*
 * Copyright 2016 the original author or authors.
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

package org.springframework.integration.sftp.inbound;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import org.springframework.integration.file.remote.AbstractFileInfo;
import org.springframework.integration.file.remote.AbstractRemoteFileStreamingMessageSource;
import org.springframework.integration.file.remote.RemoteFileTemplate;
import org.springframework.integration.sftp.session.SftpFileInfo;

import com.jcraft.jsch.ChannelSftp.LsEntry;

/**
 * Message source for streaming SFTP remote file contents.
 *
 * @author Gary Russell
 * @since 4.3
 *
 */
public class SftpStreamingMessageSource extends AbstractRemoteFileStreamingMessageSource<LsEntry> {

	/**
	 * Construct an instance with the supplied template.
	 * @param template the template.
	 */
	public SftpStreamingMessageSource(RemoteFileTemplate<LsEntry> template) {
		super(template, null);
	}

	/**
	 * Construct an instance with the supplied template and comparator.
	 * Note: the comparator is applied each time the remote directory is listed
	 * which only occurs when the previous list is exhausted.
	 * @param template the template.
	 * @param comparator the comparator.
	 */
	public SftpStreamingMessageSource(RemoteFileTemplate<LsEntry> template,
			Comparator<AbstractFileInfo<LsEntry>> comparator) {
		super(template, comparator);
	}

	@Override
	public String getComponentType() {
		return "sftp:inbound-streaming-channel-adapter";
	}

	@Override
	protected List<AbstractFileInfo<LsEntry>> asFileInfoList(Collection<LsEntry> files) {
		List<AbstractFileInfo<LsEntry>> canonicalFiles = new ArrayList<AbstractFileInfo<LsEntry>>();
		for (LsEntry file : files) {
			canonicalFiles.add(new SftpFileInfo(file));
		}
		return canonicalFiles;
	}

}
