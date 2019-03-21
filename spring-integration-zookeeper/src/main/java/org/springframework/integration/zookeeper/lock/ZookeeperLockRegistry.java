/*
 * Copyright 2015-2019 the original author or authors.
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

package org.springframework.integration.zookeeper.lock;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.integration.support.locks.ExpirableLockRegistry;
import org.springframework.messaging.MessagingException;
import org.springframework.scheduling.concurrent.ExecutorConfigurationSupport;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.Assert;

/**
 * {@link ExpirableLockRegistry} implementation using Zookeeper, or more specifically,
 * Curator {@link InterProcessMutex}.
 *
 * @author Gary Russell
 * @author Artem Bilan
 * @since 4.2
 *
 */
public class ZookeeperLockRegistry implements ExpirableLockRegistry, DisposableBean {

	private static final String DEFAULT_ROOT = "/SpringIntegration-LockRegistry";

	private final CuratorFramework client;

	private final KeyToPathStrategy keyToPath;

	private final Map<String, ZkLock> locks = new HashMap<String, ZkLock>();

	private final boolean trackingTime;

	private AsyncTaskExecutor mutexTaskExecutor = new ThreadPoolTaskExecutor();

	{
		ThreadPoolTaskExecutor threadPoolTaskExecutor = (ThreadPoolTaskExecutor) this.mutexTaskExecutor;
		threadPoolTaskExecutor.setAllowCoreThreadTimeOut(true);
		threadPoolTaskExecutor.setBeanName("ZookeeperLockRegistryExecutor");
		threadPoolTaskExecutor.initialize();
	}

	private boolean mutexTaskExecutorExplicitlySet;

	/**
	 * Construct a lock registry using the default {@link KeyToPathStrategy} which
	 * simple appends the key to '/SpringIntegration-LockRegistry/'.
	 * @param client the {@link CuratorFramework}.
	 */
	public ZookeeperLockRegistry(CuratorFramework client) {
		this(client, DEFAULT_ROOT);
	}

	/**
	 * Construct a lock registry using the default {@link KeyToPathStrategy} which
	 * simple appends the key to {@code '<root>/'}.
	 * @param client the {@link CuratorFramework}.
	 * @param root the path root (no trailing /).
	 */
	public ZookeeperLockRegistry(CuratorFramework client, String root) {
		this(client, new DefaultKeyToPathStrategy(root));
	}

	/**
	 * Construct a lock registry using the supplied {@link KeyToPathStrategy}.
	 * @param client the {@link CuratorFramework}.
	 * @param keyToPath the implementation of {@link KeyToPathStrategy}.
	 */
	public ZookeeperLockRegistry(CuratorFramework client, KeyToPathStrategy keyToPath) {
		Assert.notNull(client, "'client' cannot be null");
		Assert.notNull(client, "'keyToPath' cannot be null");
		this.client = client;
		this.keyToPath = keyToPath;
		this.trackingTime = !keyToPath.bounded();
	}

	/**
	 * Set an {@link AsyncTaskExecutor} to use when establishing (and testing) the
	 * connection with Zookeeper. This must be performed asynchronously so the
	 * {@link Lock#tryLock(long, TimeUnit)} contract can be honored. While an executor is
	 * used internally, an external executor may be required in some environments, for
	 * example those that require the use of a {@code WorkManagerTaskExecutor}.
	 * @param mutexTaskExecutor the executor.
	 *
	 * @since 4.2.10
	 */
	public void setMutexTaskExecutor(AsyncTaskExecutor mutexTaskExecutor) {
		Assert.notNull(mutexTaskExecutor, "'mutexTaskExecutor' cannot be null");
		((ExecutorConfigurationSupport) this.mutexTaskExecutor).shutdown();
		this.mutexTaskExecutor = mutexTaskExecutor;
		this.mutexTaskExecutorExplicitlySet = true;
	}

	@Override
	public Lock obtain(Object lockKey) {
		Assert.isInstanceOf(String.class, lockKey);
		String path = this.keyToPath.pathFor((String) lockKey);
		ZkLock lock = this.locks.get(path);
		if (lock == null) {
			synchronized (this.locks) {
				lock = this.locks.get(path);
				if (lock == null) {
					lock = new ZkLock(this.client, this.mutexTaskExecutor, path);
					this.locks.put(path, lock);
				}
				if (this.trackingTime) {
					lock.setLastUsed(System.currentTimeMillis());
				}
			}
		}
		return lock;
	}

	/**
	 * Remove locks last acquired more than 'age' ago that are not currently locked.
	 * Expiry is not supported if the {@link KeyToPathStrategy} is bounded (returns a finite
	 * number of paths). With such a {@link KeyToPathStrategy}, the overhead of tracking when
	 * a lock is obtained is avoided.
	 * @param age the time since the lock was last obtained.
	 */
	@Override
	public void expireUnusedOlderThan(long age) {
		if (!this.trackingTime) {
			throw new IllegalStateException("Ths KeyToPathStrategy is bounded; expiry is not supported");
		}
		synchronized (this.locks) {
			Iterator<Entry<String, ZkLock>> iterator = this.locks.entrySet().iterator();
			long now = System.currentTimeMillis();
			while (iterator.hasNext()) {
				Entry<String, ZkLock> entry = iterator.next();
				ZkLock lock = entry.getValue();
				if (now - lock.getLastUsed() > age
						&& !lock.isAcquiredInThisProcess()) {
					iterator.remove();
				}
			}
		}
	}

	@Override
	public void destroy() throws Exception {
		if (!this.mutexTaskExecutorExplicitlySet) {
			((ExecutorConfigurationSupport) this.mutexTaskExecutor).shutdown();
		}
	}

	/**
	 * Strategy to convert a lock key (e.g. aggregation correlation id) to a
	 * Zookeeper path.
	 *
	 */
	public interface KeyToPathStrategy {

		/**
		 * Return the path for the key.
		 * @param key the key.
		 * @return the path.
		 */
		String pathFor(String key);

		/**
		 * @return true if this strategy returns a bounded number of locks, removing
		 * the need for removing LRU locks.
		 */
		boolean bounded();

	}

	private static final class DefaultKeyToPathStrategy implements KeyToPathStrategy {

		private final String root;

		private DefaultKeyToPathStrategy(String rootPath) {
			Assert.notNull(rootPath, "'rootPath' cannot be null");
			if (!rootPath.endsWith("/")) {
				this.root = rootPath + "/";
			}
			else {
				this.root = rootPath;
			}
		}

		@Override
		public String pathFor(String key) {
			return this.root + key;
		}

		@Override
		public boolean bounded() {
			return false;
		}

	}

	private static final class ZkLock implements Lock {

		private final CuratorFramework client;

		private final InterProcessMutex mutex;

		private final AsyncTaskExecutor mutexTaskExecutor;

		private final String path;

		private long lastUsed;

		private ZkLock(CuratorFramework client, AsyncTaskExecutor mutexTaskExecutor, String path) {
			this.client = client;
			this.mutex = new InterProcessMutex(client, path);
			this.mutexTaskExecutor = mutexTaskExecutor;
			this.path = path;
		}

		public long getLastUsed() {
			return this.lastUsed;
		}

		public void setLastUsed(long lastUsed) {
			this.lastUsed = lastUsed;
		}

		@Override
		public void lock() {
			try {
				this.mutex.acquire();
			}
			catch (Exception e) {
				throw new RuntimeException("Failed to acquire mutex at " + this.path, e);
			}
		}

		@Override
		public void lockInterruptibly() throws InterruptedException {
			boolean locked = false;
			// this is a bit ugly, but...
			while (!locked) {
				locked = tryLock(1, TimeUnit.SECONDS);
			}

		}

		@Override
		public boolean tryLock() {
			try {
				return tryLock(1, TimeUnit.SECONDS);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return false;
			}
		}

		@Override
		public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
			Future<Boolean> future = null;
			try {
				long startTime = System.currentTimeMillis();

				future = this.mutexTaskExecutor.submit(new Callable<Boolean>() {

					@Override
					public Boolean call() throws Exception {
						return ZkLock.this.client.checkExists().forPath("/") != null;
					}

				});

				long waitTime = unit.toMillis(time);

				boolean connected = future.get(waitTime, TimeUnit.MILLISECONDS);

				if (!connected) {
					future.cancel(true);
					return false;
				}
				else {
					waitTime = waitTime - (System.currentTimeMillis() - startTime);
					return this.mutex.acquire(waitTime, TimeUnit.MILLISECONDS);
				}
			}
			catch (TimeoutException e) {
				if (future != null) {
					future.cancel(true);
				}
				return false;
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw e;
			}
			catch (Exception e) {
				throw new MessagingException("Failed to acquire mutex at " + this.path, e);
			}
		}

		@Override
		public void unlock() {
			try {
				this.mutex.release();
			}
			catch (Exception e) {
				throw new MessagingException("Failed to release mutex at " + this.path, e);
			}
		}

		@Override
		public Condition newCondition() {
			throw new UnsupportedOperationException("Conditions are not supported");
		}

		public boolean isAcquiredInThisProcess() {
			return this.mutex.isAcquiredInThisProcess();
		}

	}

}
