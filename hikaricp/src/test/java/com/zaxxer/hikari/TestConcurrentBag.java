/*
 * Copyright (C) 2013 Brett Wooldridge
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zaxxer.hikari;

import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.zaxxer.hikari.pool.HikariPool;
import com.zaxxer.hikari.pool.PoolBagEntry;
import com.zaxxer.hikari.util.ConcurrentBag;
import com.zaxxer.hikari.util.Java8ConcurrentBag;

/**
 *
 * @author Brett Wooldridge
 */
public class TestConcurrentBag
{
   private static HikariDataSource ds;

   @BeforeClass
   public static void setup()
   {
      HikariConfig config = new HikariConfig();
      config.setMinimumIdle(1);
      config.setMaximumPoolSize(2);
      config.setInitializationFailFast(true);
      config.setConnectionTestQuery("VALUES 1");
      config.setDataSourceClassName("com.zaxxer.hikari.mocks.StubDataSource");

      ds = new HikariDataSource(config);      
   }

   @AfterClass
   public static void teardown()
   {
      ds.close();
   }

   @Test
   public void testConcurrentBag() throws InterruptedException
   {
      ConcurrentBag<PoolBagEntry> bag = new Java8ConcurrentBag(null);
      Assert.assertEquals(0, bag.values(8).size());

      HikariPool pool = TestElf.getPool(ds);
      PoolBagEntry reserved = new PoolBagEntry(null, TestElf.getPool(ds));
      bag.add(reserved);
      bag.reserve(reserved);      // reserved

      PoolBagEntry inuse = new PoolBagEntry(null, pool);
      bag.add(inuse);
      bag.borrow(2L, TimeUnit.SECONDS); // in use
      
      PoolBagEntry notinuse = new PoolBagEntry(null, pool);
      bag.add(notinuse); // not in use
      
      bag.dumpState();

      try {
         bag.requite(reserved);
         Assert.fail();
      }
      catch (IllegalStateException e) {
         // pass
      }

      try {
         bag.remove(notinuse);
         Assert.fail();
      }
      catch (IllegalStateException e) {
         // pass
      }

      try {
         bag.unreserve(notinuse);
         Assert.fail();
      }
      catch (IllegalStateException e) {
         // pass
      }

      try {
         bag.remove(inuse);
         bag.remove(inuse);
         Assert.fail();
      }
      catch (IllegalStateException e) {
         // pass
      }

      bag.close();
      try {
         PoolBagEntry bagEntry = new PoolBagEntry(null, pool);
         bag.add(bagEntry);
         Assert.assertNotEquals(bagEntry, bag.borrow(100, TimeUnit.MILLISECONDS));
      }
      catch (IllegalStateException e) {
         // pass
      }

      Assert.assertNotNull(notinuse.toString());
   }
}
