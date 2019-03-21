/*
 * Copyright 2015-2018 the original author or authors.
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

package org.springframework.integration.mongodb.metadata;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import org.springframework.data.mongodb.MongoDbFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.integration.mongodb.rules.MongoDbAvailable;
import org.springframework.integration.mongodb.rules.MongoDbAvailableTests;

/**
 * @author Senthil Arumugam, Samiraj Panneer Selvam
 * @author Artem Bilan
 *
 * @since 4.2
 *
 */
public class MongoDbMetadataStoreTests extends MongoDbAvailableTests {

	private static final String DEFAULT_COLLECTION_NAME = "metadataStore";

	private final String file1 = "/remotepath/filesTodownload/file-1.txt";

	private final String file1Id = "12345";

	private MongoDbMetadataStore store = null;

	@Before
	public void configure() {
		final MongoDbFactory mongoDbFactory = this.prepareMongoFactory(DEFAULT_COLLECTION_NAME);
		this.store = new MongoDbMetadataStore(mongoDbFactory);
	}

	@MongoDbAvailable
	@Test
	public void testConfigureCustomCollection() {
		final String collectionName = "testMetadataStore";
		final MongoDbFactory mongoDbFactory = this.prepareMongoFactory(collectionName);
		final MongoTemplate template = new MongoTemplate(mongoDbFactory);
		store = new MongoDbMetadataStore(template, collectionName);
		testBasics();
	}

	@MongoDbAvailable
	@Test
	public void testConfigureFactory() {
		final MongoDbFactory mongoDbFactory = this.prepareMongoFactory(DEFAULT_COLLECTION_NAME);
		store = new MongoDbMetadataStore(mongoDbFactory);
		testBasics();
	}

	@MongoDbAvailable
	@Test
	public void testConfigureFactorCustomCollection() {
		final String collectionName = "testMetadataStore";
		final MongoDbFactory mongoDbFactory = this.prepareMongoFactory(collectionName);
		store = new MongoDbMetadataStore(mongoDbFactory, collectionName);
		testBasics();
	}

	private void testBasics() {
		String fileID = store.get(file1);
		assertNull(fileID);

		store.put(file1, file1Id);

		fileID = store.get(file1);
		assertNotNull(fileID);
		assertEquals(file1Id, fileID);
	}

	@Test
	@MongoDbAvailable
	public void testGetFromStore() {
		testBasics();
	}

	@Test
	@MongoDbAvailable
	public void testPutIfAbsent() {
		String fileID = store.get(file1);
		assertNull("Get First time, Value must not exist", fileID);

		fileID = store.putIfAbsent(file1, file1Id);
		assertNull("Insert First time, Value must return null", fileID);

		fileID = store.putIfAbsent(file1, "56789");
		assertNotNull("Key Already Exists - Insertion Failed, ol value must be returned", fileID);
		assertEquals("The Old Value must be equal to returned", file1Id, fileID);

		assertEquals("The Old Value must return", file1Id, store.get(file1));

	}

	@Test
	@MongoDbAvailable
	public void testRemove() {
		String fileID = store.remove(file1);
		assertNull(fileID);

		fileID = store.putIfAbsent(file1, file1Id);
		assertNull(fileID);

		fileID = store.remove(file1);
		assertNotNull(fileID);
		assertEquals(file1Id, fileID);

		fileID = store.get(file1);
		assertNull(fileID);
	}

	@Test
	@MongoDbAvailable
	public void testReplace() {
		boolean removedValue = store.replace(file1, file1Id, "4567");
		assertFalse(removedValue);
		String fileID = store.get(file1);
		assertNull(fileID);

		fileID = store.putIfAbsent(file1, file1Id);
		assertNull(fileID);

		removedValue = store.replace(file1, file1Id, "4567");
		assertTrue(removedValue);

		fileID = store.get(file1);
		assertNotNull(fileID);
		assertEquals("4567", fileID);
	}

}
