/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ratpack.core.http.client

import ratpack.exec.Blocking
import ratpack.exec.util.ParallelBatch
import ratpack.test.exec.ExecHarness
import spock.lang.AutoCleanup

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class HttpClientConnectionPoolSpec extends BaseHttpClientSpec {

  @AutoCleanup
  def harness = ExecHarness.harness()

  def "pool limits the number of connection and its queue"() {
    given:
    def resumeRequestLatch = new CountDownLatch(2)
    def activeCounter = new AtomicInteger()

    def poolingHttpClient = clientOf {
      it.poolSize(1).poolQueueSize(1)
    }

    otherApp {
      get {
        assert activeCounter.getAndIncrement() == 0
        resumeRequestLatch.await()
        activeCounter.decrementAndGet()
        render "ok"
      }
    }

    when:
    def request = poolingHttpClient.get(otherAppUrl()).
      map { r -> r.body.text }.
      wiretap {
        if (it.error) {
          resumeRequestLatch.countDown()
        }
      }

    def requests = harness.yield {
      ParallelBatch.of([request] * 4).yieldAll()
    }.value


    then:
    requests.findAll { it.value == "ok" }.size() == 2
    requests.findAll { it.error }.size() == 2
  }

  def "a pending acquire completes successfully or throws a TimeoutException, depending on the pool acquire timeout"() {
    given:
    def resumeRequestLatch = new CountDownLatch(1)

    def isFirstRequest = new AtomicBoolean(false)

    def poolingHttpClient = clientOf {
      it.poolSize(1)
        .poolQueueSize(2)
        .poolAcquireTimeoutMillis(500)
    }

    otherApp {
      get {
        Blocking.op({
          if (isFirstRequest.compareAndSet(false, true)) {
            sleep(100)
          } else {
            resumeRequestLatch.await(1, TimeUnit.SECONDS)
          }
        }).then({
          render "ok"
        })
      }
    }

    when:
    def request = poolingHttpClient.get(otherAppUrl()).
      map { r -> r.body.text }.
      wiretap {
        if (it.error && it.throwable instanceof TimeoutException) {
          resumeRequestLatch.countDown()
        }
      }

    def requests = harness.yield {
      ParallelBatch.of([request] * 3).yieldAll()
    }.value


    then:
    requests.findAll { it.value == "ok" }.size() == 2
    requests.findAll { it.error && it.throwable instanceof TimeoutException }.size() == 1
  }
}
