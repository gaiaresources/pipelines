package org.gbif.pipelines.ingest.utils;

import org.gbif.pipelines.parsers.config.LockConfig;
import org.gbif.wrangler.lock.Lock;
import org.gbif.wrangler.lock.Mutex;
import org.gbif.wrangler.lock.zookeeper.ZooKeeperLockFactory;
import org.gbif.wrangler.lock.zookeeper.ZookeeperSharedReadWriteMutex;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

/**
 * Utility class to create instances of ReadWrite locks using Curator Framework, Zookeeper Servers and GBIF Wrangler.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SharedLockUtils {

  /**
   * Creates an non-started instance of {@link CuratorFramework}.
   */
  private static CuratorFramework curator(LockConfig config) {
    return CuratorFrameworkFactory.builder().namespace(config.getNamespace())
        .retryPolicy(new ExponentialBackoffRetry(config.getSleepTimeMs(), config.getMaxRetries()))
        .connectString(config.getZkConnectionString())
        .build();
  }

  /**
   *
   * @param config lock configuration
   * @param action action to be executed
   * @return
   */
  private static Optional<ZookeeperSharedReadWriteMutex> doInCurator(LockConfig config, Mutex.Action action) {
    if (config.getLockName() == null) {
      action.execute();
    } else {
      try (CuratorFramework curator = curator(config)) {
        curator.start();
        ZookeeperSharedReadWriteMutex sharedReadWriteMutex = new ZookeeperSharedReadWriteMutex(curator, config.getLockingPath());
        return Optional.of(sharedReadWriteMutex);
      }
    }
    return Optional.empty();
  }

  /**
   * Performs a action in the context of write/exclusive lock.
   *
   * @param config lock configuration options
   * @param action to be performed
   */
  public static void doInWriteLock(LockConfig config, Mutex.Action action) {
    doInCurator(config, action).ifPresent(sharedReadWriteMutex ->
      sharedReadWriteMutex.createWriteMutex(config.getLockName()).doInLock(action)
    );
  }

  /**
   * Performs a action in the context of read/exclusive lock.
   *
   * @param config lock configuration options
   * @param action to be performed
   */
  public static void doInReadLock(LockConfig config, Mutex.Action action) {
    doInCurator(config, action).ifPresent(sharedReadWriteMutex ->
      sharedReadWriteMutex.createReadMutex(config.getLockName()).doInLock(action)
    );
  }
}
